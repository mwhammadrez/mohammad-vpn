// Package libv2ray exposes a minimal, gomobile-friendly control surface over
// Xray-core: start with a JSON config plus a TUN file descriptor, stop, query.
package libv2ray

import (
	"errors"
	"os"
	"runtime/debug"
	"strconv"
	"strings"
	"sync"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"

	// Register every app handler and proxy/transport implementation.
	_ "github.com/xtls/xray-core/main/distro/all"
	// The TUN inbound registers itself but is not part of distro/all.
	_ "github.com/xtls/xray-core/proxy/tun"

	"github.com/xtls/xray-core/common/log"
)

// StatusHandler receives lifecycle events and forwarded core logs.
type StatusHandler interface {
	OnStatus(level int, message string)
}

// logForwarder routes Xray's internal log stream into the Java layer.
type logForwarder struct {
	emit func(int, string)
}

func (l logForwarder) Handle(msg log.Message) {
	l.emit(0, msg.String())
}

func (l logForwarder) Severity() log.Severity {
	return log.Severity(currentLevel())
}

var (
	levelMu    sync.RWMutex
	levelValue = int(log.Severity_Debug)
)

func currentLevel() int {
	levelMu.RLock()
	defer levelMu.RUnlock()
	return levelValue
}

// Controller owns a single running Xray instance.
type Controller struct {
	mu      sync.Mutex
	inst    *core.Instance
	handler StatusHandler
}

// NewController creates a controller bound to an optional status handler.
func NewController(handler StatusHandler) *Controller {
	return &Controller{handler: handler}
}

func (c *Controller) emit(level int, msg string) {
	if c.handler != nil {
		c.handler.OnStatus(level, msg)
	}
}

// SetLogLevel sets the core log threshold: 0=debug, 1=info, 2=warning, 3=error.
func (c *Controller) SetLogLevel(level int64) {
	levelMu.Lock()
	levelValue = int(level)
	levelMu.Unlock()
}

// Start parses configContent and starts Xray with the given TUN descriptor.
func (c *Controller) Start(configContent string, tunFd int64) (err error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	defer func() {
		if r := recover(); r != nil {
			c.emit(2, "core panic: "+toString(r)+"\n"+string(debug.Stack()))
			err = errors.New("core panicked")
		}
	}()

	if c.inst != nil {
		return errors.New("core is already running")
	}
	if tunFd <= 0 {
		return errors.New("invalid tun fd")
	}
	if strings.TrimSpace(configContent) == "" {
		return errors.New("empty config")
	}

	// The TUN inbound reads the descriptor from this environment variable.
	if err := os.Setenv("XRAY_TUN_FD", strconv.FormatInt(tunFd, 10)); err != nil {
		return err
	}
	c.emit(1, "tun fd "+strconv.FormatInt(tunFd, 10))

	log.RegisterHandler(logForwarder{emit: c.emit})

	cfg, err := serial.LoadJSONConfig(strings.NewReader(configContent))
	if err != nil {
		return errors.New("invalid config: " + err.Error())
	}

	inst, err := core.New(cfg)
	if err != nil {
		return errors.New("core init failed: " + err.Error())
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return errors.New("core start failed: " + err.Error())
	}

	c.inst = inst
	c.emit(1, "xray started")
	return nil
}

// Stop shuts the instance down and releases its resources.
func (c *Controller) Stop() (err error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	defer func() {
		if r := recover(); r != nil {
			c.emit(2, "core stop panic: "+toString(r))
			err = nil
		}
	}()

	if c.inst == nil {
		return nil
	}
	closeErr := c.inst.Close()
	c.inst = nil
	c.emit(1, "xray stopped")
	if closeErr != nil {
		return errors.New("core stop failed: " + closeErr.Error())
	}
	return nil
}

// IsRunning reports whether an instance is currently held.
func (c *Controller) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.inst != nil
}

func toString(v interface{}) string {
	if s, ok := v.(string); ok {
		return s
	}
	if e, ok := v.(error); ok {
		return e.Error()
	}
	return "unknown error"
}
