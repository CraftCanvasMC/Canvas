package space.vectrix.ignite.launch.ember;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Function;
import java.util.jar.Manifest;
import org.jetbrains.annotations.NotNull;

/* package */ final class ResourceConnection implements AutoCloseable {
  private final URLConnection connection;
  private final InputStream stream;
  private final Function<URLConnection, Manifest> manifestFunction;

  /* package */ ResourceConnection(final @NotNull URL url, final @NotNull Function<URLConnection, Manifest> manifestLocator) throws IOException {
    this.connection = url.openConnection();
    this.stream = this.connection.getInputStream();
    this.manifestFunction = manifestLocator;
  }

  /* package */ int contentLength() {
    return this.connection.getContentLength();
  }

  /* package */ @NotNull InputStream stream() {
    return this.stream;
  }

  /* package */ @NotNull Manifest manifest() {
    return this.manifestFunction.apply(this.connection);
  }

  @Override
  public void close() throws Exception {
    this.stream.close();
  }
}
