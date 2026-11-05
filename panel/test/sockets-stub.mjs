/**
 * Test double for `cloudflare:sockets` backed by Node's net module.
 *
 * `connect()` returns the same shape a Worker gets: `opened`, `readable`, `writable`, `close()`.
 */
import net from 'node:net';

export function connect({ hostname, port }) {
  // The DNS bridge test redirects the fixed 8.8.8.8:53 target to a local framed server.
  const target = globalThis.__SOCKET_TARGET__ || { host: hostname, port };
  const socket = net.connect(target);
  const opened = new Promise((resolve, reject) => {
    socket.once('connect', resolve);
    socket.once('error', reject);
  });

  const readable = new ReadableStream({
    start(controller) {
      socket.on('data', (chunk) => controller.enqueue(new Uint8Array(chunk)));
      socket.on('end', () => {
        try {
          controller.close();
        } catch {}
      });
      socket.on('error', (error) => {
        try {
          controller.error(error);
        } catch {}
      });
    },
    cancel() {
      socket.destroy();
    },
  });

  const writable = new WritableStream({
    write(chunk) {
      socket.write(Buffer.from(chunk));
    },
    close() {
      socket.end();
    },
  });

  return {
    opened,
    readable,
    writable,
    close() {
      socket.destroy();
    },
  };
}
