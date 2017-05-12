package org.ruslan.hype.cycle;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.servlet.ServletContext;

import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.api.methods.send.SendPhoto;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultArticle;
import org.telegram.telegrambots.api.objects.inlinequery.result.InlineQueryResultPhoto;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.generics.BotSession;

import com.google.common.base.Preconditions;

public class HypeCycleBot extends TelegramLongPollingBot {
    private Logger log = Logger.getLogger(getClass().getName());
	private ServletContext servletContext;
	private String telegramToken;
	private String telegramBotName;
	private BotSession session;
	private TelegramBotsApi api;
	private boolean started;

    static {
        ApiContextInitializer.init();
    }

    public HypeCycleBot() {
    	telegramBotName = getSetup("TELEGRAM_BOT", telegramBotName);
    	telegramToken = getSetup("TELEGRAM_TOKEN", telegramToken);
    	Preconditions.checkArgument(telegramBotName != null && !telegramBotName.isEmpty());
    	Preconditions.checkArgument(telegramToken != null && !telegramToken.isEmpty());
	}
    
    public String getSetup(String key, String def) {
    	String vkey = key.toUpperCase().replace(".", "_");
    	String s = System.getenv(vkey);
    	if (s == null && servletContext != null) {
    		vkey = key.toLowerCase().replaceAll("_", ".");
    		s = servletContext.getInitParameter(key);
    	}
    	
    	if (s != null) {
    		s = s.trim();
    	}
    	
    	s = s != null ? s : def;
    	if (log.isLoggable(Level.FINE)) log.fine("Setup " + key + " = " + s);
    	return s;
    }
    
    protected byte[] drawImage(String keyword, int section) throws IOException {
    	BufferedImage img = ImageIO.read(getClass().getResource("/HypeCycleTemplate.png"));
    	Graphics2D g = img.createGraphics();
    	RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHints(rh);
    	g.setColor(Color.BLACK);
    	Font f = new Font ("TimesRoman", Font.BOLD, 18);
    	g.setFont(f);
    	FontMetrics fm = g.getFontMetrics();
    	int w = fm.stringWidth(keyword);
    	int h = fm.getHeight();
    	int[] coords = {
    		87,
    		168,
    		255,
    		406,
    		539
    	};
    	g.drawString(keyword, coords[section] - w / 2, 70 - h);
    	g.dispose();
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
    	
    	JPEGImageWriteParam jpegParams = new JPEGImageWriteParam(null);
    	jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    	jpegParams.setCompressionQuality(0.9f);
    	final ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
    	writer.setOutput(new MemoryCacheImageOutputStream(bos));
    	writer.write(null, new IIOImage(img, null, null), jpegParams);
    	return bos.toByteArray();
    }
    
    protected byte[] drawThumb(int section) throws IOException {
    	BufferedImage img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
    	Graphics2D g = img.createGraphics();
    	RenderingHints rh = new RenderingHints(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHints(rh);
    	g.setBackground(Color.WHITE);
    	g.clearRect(0, 0, 320, 240);
    	String[] keywords = {
    		"Innovation",
    		"Inflated",
    		"Disillusionment",
    		"Englightment",
    		"Productivity"
    	};
    	String keyword = keywords[section];
    	
    	g.setColor(Color.BLACK);
    	Font f = new Font ("TimesRoman", Font.BOLD, 38);
    	g.setFont(f);
    	FontMetrics fm = g.getFontMetrics();
    	int w = fm.stringWidth(keyword);
    	int h = fm.getHeight();
    	g.drawString(keyword, 320 / 2 - w / 2, 240 / 2 + h / 2);
    	g.dispose();
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
    	ImageIO.write(img, "jpg", bos);
    	return bos.toByteArray();
    }
    
    public String publishImage(byte[] img) throws IOException {
    	String fname = System.currentTimeMillis() + ".jpg";
    	File dir = new File(getSetup("PUBLISH_DIR", "img") + "/");
    	dir.mkdirs();
    	FileOutputStream fos = new FileOutputStream(new File(dir, fname));
    	try {
    		fos.write(img);
    		return getSetup("PUBLISH_URL", "http://localhost/img/") + fname;
    	} finally {
    		fos.close();
    	}
    }
    
    @Override
    public void onUpdateReceived(Update update) {
    	if (log.isLoggable(Level.INFO)) {
    		log.info("Update: " + update);
    	}
    	
		if (update != null && update.getInlineQuery() != null) {
	    	InlineQuery inline = update.getInlineQuery();
    		String query = inline.getQuery();
			log.info("Inline query: " + query);
    		
    		try {
    			if (query.isEmpty()) {
    				answerInlineQuery(new AnswerInlineQuery().setInlineQueryId(inline.getId()).setResults(new InlineQueryResultArticle().setId("EmptyResponse-v1").setTitle("Empty").setInputMessageContent(new InputTextMessageContent().setMessageText("No term provided!"))));
	    		} else {
					AnswerInlineQuery ans = new AnswerInlineQuery();	
					ans.setInlineQueryId(inline.getId());
					ans.setResults(makePhoto(query, 0), makePhoto(query, 1), makePhoto(query, 2), makePhoto(query, 3), makePhoto(query, 4));
					answerInlineQuery(ans);
	    		}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
		
		if (update != null && update.hasMessage() && update.getMessage().getText() != null) {
			try {
				String s = update.getMessage().getText();
				log.info("Direct message: " + s);
				sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawImage(s, 0))));
				sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawImage(s, 1))));
				sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawImage(s, 2))));
				sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawImage(s, 3))));
				sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawImage(s, 4))));
				//sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawThumb(0))));
				//sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawThumb(1))));
				//sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawThumb(2))));
				//sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawThumb(3))));
				//sendPhoto(new SendPhoto().setChatId(update.getMessage().getChatId()).setNewPhoto("test", new ByteArrayInputStream(drawThumb(4))));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
    }

	protected InlineQueryResultPhoto makePhoto(String query, int index) throws IOException {
		String url = publishImage(drawImage(query, index));
		String turl = publishImage(drawThumb(index));
		InlineQueryResultPhoto r = new InlineQueryResultPhoto();
		r.setId("test" + System.currentTimeMillis());
		r.setPhotoUrl(url);
		r.setThumbUrl(turl);
		return r;
	}
    
    @Override
    public String getBotToken() {
    	return telegramToken;
    }
    
    @Override
    public String getBotUsername() {
    	return telegramBotName;
    }

    public void start() throws TelegramApiRequestException {
        api = new TelegramBotsApi();
        session = api.registerBot(this);
        started = true;
    }
    
    public void stop() {
    	session.stop();
    	session = null;
    	started = false;
    }

	public ServletContext getServletContext() {
		return servletContext;
	}

	public void setServletContext(ServletContext servletContext) {
		this.servletContext = servletContext;
	}

	public boolean isStarted() {
		return started;
	}

	public void setStarted(boolean started) {
		this.started = started;
	}
}
