// Package libawg exposes a small gomobile-friendly control surface over
// amneziawg-go: start with a JSON config plus a TUN file descriptor, stop, query.
package libawg

import (
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"runtime/debug"
	"strconv"
	"strings"
	"sync"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
)

// StatusHandler receives lifecycle events.
type StatusHandler interface {
	OnStatus(level int, message string)
}

// Controller owns a single running AmneziaWG device.
type Controller struct {
	mu      sync.Mutex
	dev     *device.Device
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

type peerJSON struct {
	PublicKey    string   `json:"publicKey"`
	PresharedKey string   `json:"presharedKey"`
	Endpoint     string   `json:"endpoint"`
	AllowedIPs   []string `json:"allowedIPs"`
	Keepalive    int      `json:"persistentKeepalive"`
}

type obfJSON struct {
	Jc   int `json:"jc"`
	Jmin int `json:"jmin"`
	Jmax int `json:"jmax"`
	S1   int `json:"s1"`
	S2   int `json:"s2"`
	S3   int `json:"s3"`
	S4   int `json:"s4"`
	H1   int `json:"h1"`
	H2   int `json:"h2"`
	H3   int `json:"h3"`
	H4   int `json:"h4"`
}

type configJSON struct {
	PrivateKey string     `json:"privateKey"`
	ListenPort int        `json:"listenPort"`
	MTU        int        `json:"mtu"`
	Peers      []peerJSON `json:"peers"`
	Obf        *obfJSON   `json:"obf"`
}

func keyToHex(b64 string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(b64))
	if err != nil {
		return "", errors.New("invalid base64 key")
	}
	if len(raw) != 32 {
		return "", errors.New("key must be 32 bytes")
	}
	return hex.EncodeToString(raw), nil
}

func buildUAPI(cfg *configJSON) (string, error) {
	if cfg.PrivateKey == "" {
		return "", errors.New("private key missing")
	}
	priv, err := keyToHex(cfg.PrivateKey)
	if err != nil {
		return "", fmt.Errorf("private key: %w", err)
	}
	if len(cfg.Peers) == 0 {
		return "", errors.New("no peers")
	}

	var b strings.Builder
	fmt.Fprintf(&b, "private_key=%s\n", priv)
	fmt.Fprintf(&b, "listen_port=%d\n", cfg.ListenPort)
	b.WriteString("replace_peers=true\n")

	if cfg.Obf != nil {
		o := cfg.Obf
		if o.Jc > 0 {
			fmt.Fprintf(&b, "jc=%d\n", o.Jc)
		}
		if o.Jmin > 0 {
			fmt.Fprintf(&b, "jmin=%d\n", o.Jmin)
		}
		if o.Jmax > 0 {
			fmt.Fprintf(&b, "jmax=%d\n", o.Jmax)
		}
		for i, v := range []int{o.S1, o.S2, o.S3, o.S4} {
			if v > 0 {
				fmt.Fprintf(&b, "s%d=%d\n", i+1, v)
			}
		}
		for i, v := range []int{o.H1, o.H2, o.H3, o.H4} {
			if v > 0 {
				fmt.Fprintf(&b, "h%d=%d\n", i+1, v)
			}
		}
	}

	for _, p := range cfg.Peers {
		pub, err := keyToHex(p.PublicKey)
		if err != nil {
			return "", fmt.Errorf("peer public key: %w", err)
		}
		fmt.Fprintf(&b, "public_key=%s\n", pub)
		if p.PresharedKey != "" {
			psk, err := keyToHex(p.PresharedKey)
			if err != nil {
				return "", fmt.Errorf("preshared key: %w", err)
			}
			fmt.Fprintf(&b, "preshared_key=%s\n", psk)
		}
		if p.Endpoint != "" {
			fmt.Fprintf(&b, "endpoint=%s\n", p.Endpoint)
		}
		for _, ip := range p.AllowedIPs {
			fmt.Fprintf(&b, "allowed_ip=%s\n", ip)
		}
		if p.Keepalive > 0 {
			fmt.Fprintf(&b, "persistent_keepalive_interval=%d\n", p.Keepalive)
		}
	}
	b.WriteString("\n")
	return b.String(), nil
}

// Start creates the TUN device from the descriptor, brings the device up and
// applies the configuration.
func (c *Controller) Start(configContent string, tunFd int64, mtu int64) (err error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	defer func() {
		if r := recover(); r != nil {
			c.emit(2, "awg panic: "+fmt.Sprint(r)+"\n"+string(debug.Stack()))
			err = errors.New("awg panicked")
		}
	}()

	if c.dev != nil {
		return errors.New("awg is already running")
	}
	if tunFd <= 0 {
		return errors.New("invalid tun fd")
	}
	if mtu <= 0 {
		mtu = 1400
	}

	var cfg configJSON
	if err := json.Unmarshal([]byte(configContent), &cfg); err != nil {
		return errors.New("invalid awg config: " + err.Error())
	}
	uapi, err := buildUAPI(&cfg)
	if err != nil {
		return err
	}

	file := os.NewFile(uintptr(tunFd), "tun")
	if file == nil {
		return errors.New("could not adopt tun fd")
	}
	tunDev, err := tun.CreateTUNFromFile(file, int(mtu))
	if err != nil {
		return errors.New("tun adopt failed: " + err.Error())
	}

	logger := device.NewLogger(device.LogLevelVerbose, "awg: ")
	dev := device.NewDevice(tunDev, conn.NewDefaultBind(), logger)
	if err := dev.IpcSet(uapi); err != nil {
		dev.Close()
		return errors.New("awg config rejected: " + err.Error())
	}
	if err := dev.Up(); err != nil {
		dev.Close()
		return errors.New("awg up failed: " + err.Error())
	}

	c.dev = dev
	c.emit(1, "awg started, peers="+strconv.Itoa(len(cfg.Peers)))
	return nil
}

// Stop closes the device and releases the tunnel.
func (c *Controller) Stop() (err error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	defer func() {
		if r := recover(); r != nil {
			c.emit(2, "awg stop panic: "+fmt.Sprint(r))
			err = nil
		}
	}()

	if c.dev == nil {
		return nil
	}
	c.dev.Close()
	c.dev = nil
	c.emit(1, "awg stopped")
	return nil
}

// IsRunning reports whether a device is currently held.
func (c *Controller) IsRunning() bool {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.dev != nil
}
