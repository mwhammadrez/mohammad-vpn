package libmohammad

import (
	"errors"
	"net/url"
	"strconv"
	"sync"

	"gvisor.dev/gvisor/pkg/tcpip/stack"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"github.com/xjasonlyu/tun2socks/v2/proxy"
	"github.com/xjasonlyu/tun2socks/v2/tunnel"
)

// TunBridge adapts a VpnService descriptor to the local SOCKS inbound.
//
// It owns a duplicate of the descriptor: the Java ParcelFileDescriptor stays
// the original owner and is closed by the service.
type TunBridge struct {
	mu  sync.Mutex
	stk *stack.Stack
}

// NewTunBridge creates a bridge with no running stack.
func NewTunBridge() *TunBridge {
	return &TunBridge{}
}

// Start brings the user-space stack up on fd and forwards TCP/UDP to proxyAddr.
func (b *TunBridge) Start(fd int64, mtu int64, proxyAddr string) (err error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	if b.stk != nil {
		return errors.New("bridge is already running")
	}
	if fd <= 0 {
		return errors.New("invalid tun fd")
	}
	if mtu <= 0 {
		mtu = 1500
	}

	defer func() {
		if r := recover(); r != nil {
			b.stk = nil
			err = errors.New("tun2socks panic")
		}
	}()

	dev, err := fdbased.Open(strconv.FormatInt(fd, 10), uint32(mtu), 0)
	if err != nil {
		return errors.New("device open failed: " + err.Error())
	}

	u, err := url.Parse(proxyAddr)
	if err != nil {
		dev.Close()
		return errors.New("bad proxy address: " + err.Error())
	}
	p, err := proxy.Parse(u)
	if err != nil {
		dev.Close()
		return errors.New("proxy parse failed: " + err.Error())
	}
	tunnel.T().SetProxy(p)

	stk, err := core.CreateStack(&core.Config{
		LinkEndpoint:     dev,
		TransportHandler: tunnel.T(),
	})
	if err != nil {
		dev.Close()
		return errors.New("stack create failed: " + err.Error())
	}

	b.stk = stk
	return nil
}

// Stop tears the stack down. Safe to call when not running.
func (b *TunBridge) Stop() (err error) {
	b.mu.Lock()
	defer b.mu.Unlock()

	defer func() {
		if r := recover(); r != nil {
			b.stk = nil
			err = nil
		}
	}()

	if b.stk == nil {
		return nil
	}
	b.stk.Close()
	b.stk.Wait()
	b.stk = nil
	return nil
}

// IsRunning reports whether a stack is held.
func (b *TunBridge) IsRunning() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.stk != nil
}
