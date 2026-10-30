package libmohammad

import (
	"os"
	"testing"

	"golang.org/x/sys/unix"
)

func fdIsOpen(fd uintptr) bool {
	_, err := unix.FcntlInt(fd, unix.F_GETFD, 0)
	return err == nil
}

func countOpenFDs(t *testing.T) int {
	t.Helper()
	entries, err := os.ReadDir("/proc/self/fd")
	if err != nil {
		t.Skipf("cannot read /proc/self/fd: %v", err)
	}
	return len(entries)
}

// Closing the bridge must never close the descriptor owned by the caller
// (Java's ParcelFileDescriptor). The bridge works on a duplicate.
func TestStopDoesNotCloseCallerFD(t *testing.T) {
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatalf("pipe: %v", err)
	}
	defer r.Close()
	defer w.Close()

	bridge := NewTunBridge()
	startErr := bridge.Start(int64(r.Fd()), 1400, "socks5://127.0.0.1:1")
	if startErr == nil {
		if err := bridge.Stop(); err != nil {
			t.Fatalf("stop: %v", err)
		}
	} else {
		t.Logf("start returned %v (device rejected the pipe); ownership still applies", startErr)
	}

	if !fdIsOpen(r.Fd()) {
		t.Fatal("bridge closed the caller-owned descriptor")
	}
}

// A second Stop must be a no-op and must not touch unrelated descriptors,
// which is the observable part of the double-close hazard.
func TestDoubleStopIsIdempotent(t *testing.T) {
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatalf("pipe: %v", err)
	}
	defer r.Close()
	defer w.Close()

	bridge := NewTunBridge()
	if err := bridge.Start(int64(r.Fd()), 1400, "socks5://127.0.0.1:1"); err != nil {
		t.Skipf("device not usable on this host: %v", err)
	}
	if err := bridge.Stop(); err != nil {
		t.Fatalf("first stop: %v", err)
	}
	if err := bridge.Stop(); err != nil {
		t.Fatalf("second stop must be a no-op, got %v", err)
	}

	// A descriptor opened after the double stop must still be valid.
	probe, err := os.Open("/dev/null")
	if err != nil {
		t.Fatalf("probe open: %v", err)
	}
	defer probe.Close()
	if !fdIsOpen(probe.Fd()) {
		t.Fatal("unrelated descriptor was closed by the bridge")
	}
	if !fdIsOpen(r.Fd()) {
		t.Fatal("caller descriptor was closed by the bridge")
	}
}

// Startup failure must release everything it acquired.
func TestStartFailureReleasesResources(t *testing.T) {
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatalf("pipe: %v", err)
	}
	defer r.Close()
	defer w.Close()

	before := countOpenFDs(t)

	bridge := NewTunBridge()
	if err := bridge.Start(-1, 1400, "socks5://127.0.0.1:1"); err == nil {
		bridge.Stop()
		t.Fatal("expected failure for an invalid descriptor")
	}

	after := countOpenFDs(t)
	if after > before {
		t.Fatalf("descriptor leak on failed start: %d -> %d", before, after)
	}
	if !fdIsOpen(r.Fd()) {
		t.Fatal("failed start closed the caller descriptor")
	}
}
