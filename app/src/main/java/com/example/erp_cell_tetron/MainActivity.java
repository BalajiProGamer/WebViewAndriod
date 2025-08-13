package com.example.erp_cell_tetron;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Map;
import java.util.HashMap;

import androidx.browser.customtabs.CustomTabsIntent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.util.Log;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private final Set<String> inFlightPdfs =
            Collections.synchronizedSet(new HashSet<>());

    private static final String LOGIN_URL = "https://accounts.google.com";
    private static final String HOME_URL  = "https://srishty.bharathuniv.ac.in/";
    private static final String REDIRECT_URL = HOME_URL;

    private WebView myWeb;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> pickAny;
    private boolean launchedCustomTab = false;

    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pickAny = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                (ActivityResult result) -> {
                    if (filePathCallback != null) {
                        Uri uri = (result.getData() != null) ? result.getData().getData() : null;
                        filePathCallback.onReceiveValue(uri != null ? new Uri[]{uri} : null);
                        filePathCallback = null;
                    }
                }
        );

        myWeb = findViewById(R.id.myWeb);
        if (myWeb == null) return;

        WebSettings s = myWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(myWeb, true);
        }

        myWeb.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                // After any normal navigation, ensure scrolling is enabled and no stale overlays remain
                cleanupOverlaysAndUnlockScroll();
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError error) {
                if (req != null && req.getUrl() != null && looksLikePdf(req.getUrl().toString())) {
                    return; // ignore the iframe's failed PDF navigation
                }
                super.onReceivedError(view, req, error);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse resp) {
                if (req != null && req.getUrl() != null && looksLikePdf(req.getUrl().toString())) {
                    return; // ignore the iframe's HTTP error for the PDF
                }
                super.onReceivedHttpError(view, req, resp);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(LOGIN_URL)) {
                    openInCustomTab(url);
                    return true;
                }
                if (looksLikePdf(url)) {
                    handlePdfUrl(url, request.getRequestHeaders()); // ← forward headers from WebView
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url != null && url.startsWith(LOGIN_URL)) {
                    openInCustomTab(url);
                    return true;
                }
                if (url != null && looksLikePdf(url)) {
                    Map<String,String> hdr = new HashMap<>();
                    String ua = myWeb.getSettings().getUserAgentString();
                    String ref = myWeb.getUrl();
                    String ck  = CookieManager.getInstance().getCookie(url);
                    if (ua != null)  hdr.put("User-Agent", ua);
                    if (ref != null) hdr.put("Referer", ref);
                    if (ck != null)  hdr.put("Cookie", ck);
                    hdr.put("Accept", "application/pdf,*/*");
                    handlePdfUrl(url, hdr); // ← forward best-effort headers
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (looksLikePdf(url)) {
                    mainHandler.post(() -> handlePdfUrl(url, request.getRequestHeaders()));

                    // Feed the iframe a tiny blank HTML so it doesn't show the error page
                    String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                            + "<body style='margin:0;background:transparent;'></body></html>";
                    WebResourceResponse resp = new WebResourceResponse(
                            "text/html", "utf-8",
                            new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    );
                    if (Build.VERSION.SDK_INT >= 21) {
                        resp.setStatusCodeAndReasonPhrase(200, "OK");
                    }
                    return resp;
                }
                return super.shouldInterceptRequest(view, request);
            }

        });

        myWeb.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;

                String[] mimeTypes = normalizeAcceptToMimes(params != null ? params.getAcceptTypes() : null);

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE);
                if (mimeTypes.length == 0) {
                    intent.setType("application/pdf");
                } else if (mimeTypes.length == 1) {
                    intent.setType(mimeTypes[0]);
                } else {
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                }

                try {
                    pickAny.launch(intent);
                } catch (ActivityNotFoundException e) {
                    try {
                        Intent fallback = new Intent(Intent.ACTION_GET_CONTENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*");
                        pickAny.launch(fallback);
                    } catch (ActivityNotFoundException e2) {
                        if (filePathCallback != null) {
                            filePathCallback.onReceiveValue(null);
                            filePathCallback = null;
                        }
                    }
                }
                return true;
            }

            // Ensure target=_blank / window.open stays inside our activity
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView temp = new WebView(view.getContext());
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView v, String url) {
                        if (looksLikePdf(url)) {
                            HashMap<String,String> hdr = new HashMap<>();
                            String ua  = myWeb.getSettings().getUserAgentString();
                            String ref = myWeb.getUrl();
                            String ck  = CookieManager.getInstance().getCookie(url);
                            if (ua  != null) hdr.put("User-Agent", ua);
                            if (ref != null) hdr.put("Referer", ref);
                            if (ck  != null) hdr.put("Cookie", ck);
                            hdr.put("Accept", "application/pdf,*/*");
                            handlePdfUrl(url, hdr);     // ← forward best-possible headers
                        } else {
                            mainHandler.post(() -> myWeb.loadUrl(url));
                        }
                        v.destroy();
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        myWeb.loadUrl(HOME_URL);
    }

    private void openInCustomTab(String url) {
        launchedCustomTab = true;
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always unlock scroll + remove any leftover overlays when returning to this Activity
        cleanupOverlaysAndUnlockScroll();

        if (launchedCustomTab && myWeb != null) {
            myWeb.loadUrl(REDIRECT_URL);
            launchedCustomTab = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        ioPool.shutdownNow();
        super.onDestroy();
    }

    private String[] normalizeAcceptToMimes(@Nullable String[] accept) {
        if (accept == null || accept.length == 0) return new String[]{"application/pdf"};
        ArrayList<String> out = new ArrayList<>();
        MimeTypeMap map = MimeTypeMap.getSingleton();

        for (String raw : accept) {
            if (raw == null) continue;
            String a = raw.trim();
            if (a.isEmpty() || a.equals("*/*")) return new String[0];
            if (a.startsWith(".")) {
                String ext = a.substring(1).toLowerCase(Locale.ROOT);
                String mime = map.getMimeTypeFromExtension(ext);
                if (mime != null && !out.contains(mime)) out.add(mime);
            } else if (a.contains("/")) {
                if (!out.contains(a)) out.add(a);
            } else {
                String mime = map.getMimeTypeFromExtension(a.toLowerCase(Locale.ROOT));
                if (mime != null && !out.contains(mime)) out.add(mime);
            }
        }
        if (out.isEmpty()) out.add("application/pdf");
        return out.toArray(new String[0]);
    }

    // --- PDF interception helpers ---

    private boolean looksLikePdf(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        // basic heuristic; covers "?file=xyz.pdf" and direct links
        return u.contains(".pdf") || u.contains("content-type=application/pdf");
    }

    private void handlePdfUrl(String url) { handlePdfUrl(url, null); }

    private void handlePdfUrl(String url, @Nullable Map<String,String> headers) {
        final String key = url;
        if (!inFlightPdfs.add(key)) return; // de-dupe rapid repeats

        ioPool.execute(() -> {
            File tmp = null;
            try {
                tmp = downloadPdfToCache(url, headers);
                if (tmp != null && tmp.exists()) {
                    Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                            this, getPackageName() + ".fileprovider", tmp
                    );
                    Intent i = new Intent(this, PdfViewerActivity.class)
                            .setData(fileUri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    mainHandler.post(() -> {
                        // Clean overlays & unlock scroll BEFORE launching viewer
                        cleanupOverlaysAndUnlockScroll();

                        startActivity(i);

                        // After viewer opens, clean again in case site added a backdrop late
                        mainHandler.postDelayed(this::cleanupOverlaysAndUnlockScroll, 250);
                    });

                } else {
                    // Fallback: ask Android to open the URL directly
                    mainHandler.post(() -> {
                        try {
                            Intent ext = new Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.parse(url), "application/pdf");
                            startActivity(ext);
                        } catch (Exception ignored) { }
                    });
                }
            } catch (Exception ignored) {
            } finally {
                inFlightPdfs.remove(key);
            }
        });
    }

    private File downloadPdfToCache(String urlStr, @Nullable Map<String,String> headers) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true);

        // 1) Forward all headers we got from WebView
        if (headers != null) {
            for (Map.Entry<String,String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
        }

        // 2) Ensure critical headers are present
        if (headers == null || !headers.containsKey("Cookie")) {
            String cookies = CookieManager.getInstance().getCookie(urlStr);
            if (cookies != null) conn.setRequestProperty("Cookie", cookies);
        }
        if (headers == null || !headers.containsKey("User-Agent")) {
            String ua = myWeb.getSettings().getUserAgentString();
            if (ua != null) conn.setRequestProperty("User-Agent", ua);
        }
        if (headers == null || !headers.containsKey("Referer")) {
            String ref = myWeb.getUrl();
            if (ref != null) conn.setRequestProperty("Referer", ref);
        }
        if (headers == null || !headers.containsKey("Accept")) {
            conn.setRequestProperty("Accept", "application/pdf,*/*");
        }

        conn.connect();

        int code = conn.getResponseCode();
        String ct  = conn.getContentType();
        String cd  = conn.getHeaderField("Content-Disposition");

        Log.d("PDF", "HTTP " + code + " ct=" + ct + " cd=" + cd + " url=" + urlStr);

        // 3) Follow redirects
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            conn.disconnect();
            return (loc != null) ? downloadPdfToCache(loc, headers) : null;
        }
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            return null;
        }

        // 4) Accept real PDFs and octet-stream with filename, or URL ending in .pdf
        boolean looksPdfByType = ct != null && ct.toLowerCase(Locale.ROOT).startsWith("application/pdf");
        boolean looksPdfByDisp = cd != null && cd.toLowerCase(Locale.ROOT).contains(".pdf");
        boolean looksPdfByUrl  = urlStr.toLowerCase(Locale.ROOT).contains(".pdf");
        if (!(looksPdfByType || looksPdfByDisp || looksPdfByUrl)) {
            conn.disconnect();
            return null;
        }

        // 5) Stream to app cache (ephemeral)
        InputStream in = new BufferedInputStream(conn.getInputStream());
        File outFile = new File(getCacheDir(), "receipt_" + UUID.randomUUID() + ".pdf");
        FileOutputStream out = new FileOutputStream(outFile);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) >= 0) { out.write(buf, 0, n); }
        out.flush(); out.close(); in.close();
        conn.disconnect();
        return outFile;
    }

    // --- Overlay cleanup & scroll unlock ---

    private void cleanupOverlaysAndUnlockScroll() {
        if (myWeb == null) return;
        String js =
                "(function(){try{"
                        + "var rm=function(sel){document.querySelectorAll(sel).forEach(function(el){"
                        + " if(el && el.parentNode){el.parentNode.removeChild(el);} });};"
                        // common PDF iframes / previewers
                        + "rm('iframe[src*=\".pdf\" i]'); rm('iframe[data-pdf]'); rm('iframe.pdf');"
                        // common modal roots / backdrops from MUI, Ant, Bootstrap, generic
                        + "rm('[role=\"dialog\"]'); rm('.MuiDialog-root'); rm('.MuiModal-root'); rm('.ReactModalPortal');"
                        + "rm('.modal'); rm('.modal-backdrop'); rm('.ant-modal-root'); rm('.ant-modal-mask'); rm('.MuiBackdrop-root');"
                        + "rm('[data-testid=\"backdrop\"]');"
                        // unlock scrolling on html/body
                        + "var html=document.documentElement, body=document.body;"
                        + "var clearLock=function(el){ if(!el) return;"
                        + " el.style.setProperty('overflow','auto','important');"
                        + " el.style.removeProperty('position');"
                        + " el.style.removeProperty('top'); el.style.removeProperty('left');"
                        + " el.style.removeProperty('right'); el.style.removeProperty('bottom');"
                        + " el.style.removeProperty('height'); el.style.removeProperty('width');"
                        + " el.classList.remove('MuiModal-open','modal-open','ant-scrolling-effect');"
                        + "};"
                        + "clearLock(html); clearLock(body);"
                        + "}catch(e){}})();";
        myWeb.evaluateJavascript(js, null);
    }
}
