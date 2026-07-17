package org.freeplane.main.application;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.freeplane.core.util.Compat;
import org.freeplane.core.util.Hyperlink;
import org.freeplane.core.util.LogUtils;
import org.freeplane.features.mode.Controller;

public class Browser {

    public void openDocument(final Hyperlink link) {
    	final URI uri = preprocessUri(link);
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                if ("file".equalsIgnoreCase(uri.getScheme())) {
					if (desktop.isSupported(Desktop.Action.OPEN)) {
					    desktop.open(new File(uri));
					    return;
					}
				} else {
					if ("mailto".equalsIgnoreCase(uri.getScheme()) && desktop.isSupported(Desktop.Action.MAIL)) {
					    desktop.mail(uri);
					    return;
					}
					if (desktop.isSupported(Desktop.Action.BROWSE)) {
					    desktop.browse(uri);
					    return;
					}
				}
            }
        } catch (Exception ignored) {
            LogUtils.warn(ignored);
        }
        openWithPlatformFallback(uri);
    }

    private URI preprocessUri(Hyperlink link) {
        try {
			String uriString = normalizeUncPrefix(link.toString());

			if (!uriString.equals(link.toString())) {
			    return new URI(uriString);
			}

			if ("smb".equalsIgnoreCase(link.getScheme()) && Compat.isWindowsOS()) {
			    String unc = Compat.smbUri2unc(link.getUri());
			    return new File(unc).toURI();
			}

			return normalizeUri(link.getUri());
		} catch (Exception e) {
			return link.getUri();
		}
    }

    private String normalizeUncPrefix(String uriString) {
        final String UNC_PREFIX = "file:////";
        if (uriString.startsWith(UNC_PREFIX)) {
            return "file://" + uriString.substring(UNC_PREFIX.length());
        }
        return uriString;
    }

    private URI normalizeUri(URI uri) throws Exception {
        if (uri == null) return null;

        String rawPath = uri.getRawPath();
        if (rawPath == null) return uri;

        // Check if the path needs any processing at all
        if (isProperlyEncoded(rawPath)) {
            return uri;
        }

        return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment());
    }

    private boolean isProperlyEncoded(String path) {
        try {
            // A properly encoded path should:
            // 1. Not contain unencoded special characters (except /)
            // 2. When decoded and re-encoded, should match the original

            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            String[] segments = decoded.split("/", -1);
            StringBuilder reencoded = new StringBuilder();

            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    reencoded.append("/");
                }
                if (!segments[i].isEmpty()) {
                    reencoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8.name())
                                               .replace("+", "%20"));
                }
            }

            return path.equals(reencoded.toString());
        } catch (Exception e) {
            return false; // If decoding fails, it's not properly encoded
        }
    }

    private void openWithPlatformFallback(URI uri) {
        String uriString = normalizeUncPrefix(uri.toString());

        String scheme = uri.getScheme();
        try {
            if (Compat.isWindowsOS()) {
 // UNSAFE:
//                if ("file".equalsIgnoreCase(scheme) || "smb".equalsIgnoreCase(scheme) || uriString.startsWith("mailto:")) {
//                    Controller.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uriString});
//                } else {
//                    Controller.exec(new String[]{"cmd.exe", "/c", "start", "", uriString});
//                }
                Controller.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", uriString});
            } else if (Compat.isMacOsX()) {
                if ("file".equalsIgnoreCase(scheme)) {
                    uriString = uri.getPath();
                }
                Controller.exec(new String[]{"open", uriString});
            } else {
                Controller.exec(new String[]{"xdg-open", uriString});
            }
        } catch (IOException ex) {
            System.err.println("Caught: " + ex);
        }
    }

}