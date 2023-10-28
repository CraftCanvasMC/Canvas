/*
 * This file is part of Ignite, licensed under the MIT License (MIT).
 *
 * Copyright (c) vectrix.space <https://vectrix.space/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package cpw.mods.modlauncher;

import java.net.MalformedURLException;
import javax.annotation.Nullable;

import java.net.URL;
import java.security.*;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

public class SecureJarHandler {
  // Ignite begin
  @SuppressWarnings("ConstantConditions")
  public static CodeSource createCodeSource(final String name, @Nullable final URL url, final byte[] bytes, @Nullable final Manifest manifest) {
    if(url == null) return new CodeSource(null, (CodeSigner[]) null);
    String path = url.getPath();
    if(path.contains("!")) path = path.substring(0, path.indexOf('!'));
    try {
      return new CodeSource(new URL(path), (CodeSigner[]) null);
    } catch(final MalformedURLException exception) {
      return new CodeSource(url, (CodeSigner[]) null);
    }
  }
  // Ignite end

  private static final Map<CodeSource, ProtectionDomain> pdCache = new HashMap<>();
  public static ProtectionDomain createProtectionDomain(CodeSource codeSource, ClassLoader cl) {
    synchronized (pdCache) {
      return pdCache.computeIfAbsent(codeSource, cs->{
        Permissions perms = new Permissions();
        perms.add(new AllPermission());
        return new ProtectionDomain(codeSource, perms, cl, null);
      });
    }
  }

  public static boolean canHandleSecuredJars() {
    return false; // Ignite
  }
}
