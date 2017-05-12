package org.ruslan.hype.cycle;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Micro HTTP client
 * 
 * @author Ruslan
 */
public class uHttp {
	private Logger log = Logger.getLogger(getClass().getName());
	private String httpUsername;
	private String httpPassword;
	private String httpJwtToken;
    // One browser to rule them all
    private CookieManager cookies = new CookieManager();
	
	public enum Method {
        POST,
        GET
    }

    static class Response {
        int responseCode;
        String response;
        byte[] responseData;
    }
    
    public String load(String url) throws IOException {
    	Response r = call(Method.GET, url, null, Auth.NONE);
    	return r.response;
    }
    
    public enum Auth {
    	NONE,
    	BASIC,
    	JWT
    }
    
    public Response call(Method method, String url, String post, Auth auth) throws IOException {
    	return call(method, url, post, null, auth);
    }
    
    public static String createBinaryData(HttpURLConnection conn, File[] files) throws IOException {
    	String boundary = "deadbeef" + (new Date().getTime());
        String data = "";
        for (int i = 0; i < files.length; i++) {
            data += "--" + boundary + "\n";
            String fname = files[i].getName();
            data += "Content-Disposition: form-data; name=\"file" + (i + 1) + "\"; filename=\"" + fname + "\"\r\n";
            data += "Content-Type: text/xml\r\n";
            data += "\r\n";
            data += getFileContent(files[i], "UTF-8");
            data += "\r\n";
        }
        data += "--" + boundary + "--";
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        return data;
    }

    public static String getFileContent(File file, String encoding) throws IOException {
		FileInputStream fis = new FileInputStream(file);
		try {
			InputStreamReader isr = new InputStreamReader(fis, encoding);
			char[] buf = new char[2048];
			int c = 0;
			StringBuilder s = new StringBuilder();
			while ((c = isr.read(buf)) != -1) {
				s.append(buf, 0, c);
			}
			return s.toString();
		} finally {
			fis.close();
		}
	}

    
    /**
     * HTTP запрос с авторизацией
     *
     * @param method HTTP метод
     * @param url    Путь запроса
     * @param post   Данные отправляемые при HTTP методе - POST
     * @return Код и текст ответа
     * @throws IOException При установки HTTP метода, при некорректных отправляемых данных, при некорректном ответе
     */
    public Response call(Method method, String url, String post, File[] files, Auth auth) throws IOException {
        String tmpMethod = method != null ? method.toString() : Method.GET.toString();
        log.finer(String.format("Call [method: %s, url: %s, post: %s]", tmpMethod, url, post));

        URL uc = new URL(url);
        URI uri = null;
        try {
        	uri = uc.toURI();
        } catch (Exception e) {
        	throw new IOException(e.getMessage(), e);
        }
        HttpURLConnection u = (HttpURLConnection) uc.openConnection();
        u.setRequestMethod(tmpMethod);
        u.setRequestProperty("Accept", "application/json");
        if (auth == Auth.BASIC) {
        	u.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s", httpUsername, httpPassword).getBytes()));
        } else 
        if (auth == Auth.JWT) {
        	u.setRequestProperty("Authorization", "Bearer " + httpJwtToken);
        }
        
        if (cookies.getCookieStore().getCookies().size() > 0) {
        	Map<String, List<String>> urlck = cookies.get(uri, new HashMap<String,List<String>>());
        	StringBuilder ck = new StringBuilder();
        	for (Iterator<String> it = urlck.keySet().iterator(); it.hasNext();) {
        		String n = it.next();
        		for (String v: urlck.get(n)) {
        			if (ck.length() > 0) {
        				ck.append("; ");
        			}
        			ck.append(v);
        		}
        	}
        	log.info("Using cookie for " + url + ": " + ck);
        	u.setRequestProperty("Cookie", ck.toString());
        }
        
        u.setDoInput(true);

        if (files != null) {
            u.setDoOutput(true);
            post = createBinaryData(u, files);
            try (OutputStream os = u.getOutputStream()) {
                try (OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8")) {
                    osw.write(post);
                    osw.flush();
                    os.flush();
                }
            }
        } else
        if (post != null) {
            u.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            u.setDoOutput(true);
            try (OutputStream os = u.getOutputStream()) {
                try (OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8")) {
                    osw.write(post);
                    osw.flush();
                    os.flush();
                }
            }
        }

        Response r = new Response();
        r.responseCode = u.getResponseCode();
        InputStream is = r.responseCode > 399 ? u.getErrorStream() : u.getInputStream();
    	String ctype = u.getHeaderField("Content-Type");
    	log.info("Call " + url + " got " + r.responseCode + " " + ctype);
    	
    	List<String> newCookies = u.getHeaderFields().get("Set-Cookie");
    	if (newCookies != null) {
    		for (String setCookieHeader: newCookies) {
    			HttpCookie ck = HttpCookie.parse(setCookieHeader).get(0);
    			log.info("Set cookie " + ck);
    			cookies.getCookieStore().add(uri, ck);
    		}
    	}
    	
        if (is != null) {
        	if (ctype == null || ctype.indexOf("text/") >= 0 || ctype.indexOf("application/j") >= 0) {
	            try {
	                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
	                BufferedReader br = new BufferedReader(isr);
	                StringBuilder buf = new StringBuilder();
	                String l = null;
	                do {
	                	l = br.readLine();
	                	if (l != null) {
	                		buf.append(l);
	                		buf.append("\n");
	                	}
	                } while (l != null);
	                r.response = buf.toString();
	                if (r.responseCode > 399) {
	                    throw new IOException(String.valueOf(r.responseCode));
	                }
	                return r;
	            } finally {
	                is.close();
	            }
        	} else {
        		byte[] bb = new byte[2048];
        		int c = 0;
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
        		while ((c = is.read(bb)) >= 0) {
        			bos.write(bb, 0, c);
        		}
        		r.responseData = bos.toByteArray();
        		return r;
        	}
        } else {
            if (r.responseCode > 399) {
                throw new IOException(String.valueOf(r.responseCode));
            } else {
                return r;
            }
        }
    }

	public String getHttpUsername() {
		return httpUsername;
	}

	public void setHttpUsername(String httpUsername) {
		this.httpUsername = httpUsername;
	}

	public String getHttpPassword() {
		return httpPassword;
	}

	public void setHttpPassword(String httpPassword) {
		this.httpPassword = httpPassword;
	}

	public String getHttpJwtToken() {
		return httpJwtToken;
	}

	public void setHttpJwtToken(String httpJwtToken) {
		this.httpJwtToken = httpJwtToken;
	}

	public CookieManager getCookies() {
		return cookies;
	}

	public void setCookies(CookieManager cookies) {
		this.cookies = cookies;
	}
}
