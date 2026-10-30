package libmohammad

import (
	"errors"
	"net/url"
	"strconv"
	"sync"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip/stack"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"github.com/xjasonlyu/tun2socks/v2/proxy"
	// Registers the "socks5://" scheme with proxy.Parse.
	_ "github.com/xjasonlyu/tun2socks/v2/proxy/socks5"
	"github.com/xjasonlyu/tun2socks/v2/tunnel"
)

// TunBridge adapts a VpnService descriptor to the local SOCKS inbound.
//
// It owns a duplicate of the descriptor: the Java ParcelFileDescriptor stays
// the original owner and is closed by the service.
type TunBridge struct {
	mu  sync.Mutex
	stk *stack.Stack
	dev device.Device
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
			if b.stk != nil {
				b.stk.Close()
				b.stk.Wait()
				b.stk = nil
			}
			if b.dev != nil {
				b.dev.Close()
				b.dev = nil
			}
			err = errors.New("tun2socks panic")
		}
	}()

	// The caller keeps ownership of fd. Duplicate it so that closing the
	// bridge can never close the ParcelFileDescriptor owned by Java, and so a
	// second close cannot hit a recycled descriptor number.
	dupFd, err := unix.Dup(int(fd))
	if err != nil {
		return errors.New("tun fd dup failed: " + err.Error())
	}
	if err := unix.SetNonblock(dupFd, true); err != nil {
		_ = unix.Close(dupFd)
		return errors.New("tun fd nonblock failed: " + err.Error())
	}

	dev, err := fdbased.Open(strconv.Itoa(dupFd), uint32(mtu), 0)
	if err != nil {
		_ = unix.Close(dupFd)
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
	b.dev = dev
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
	if b.dev != nil {
		// Closes only the duplicated descriptor held by the device.
		b.dev.Close()
		b.dev = nil
	}
	return nil
}

// IsRunning reports whether a stack is held.
func (b *TunBridge) IsRunning() bool {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.stk != nil
}
