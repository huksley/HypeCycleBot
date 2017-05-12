package org.ruslan.hype.cycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import rx.Observable;

@WebListener
public class StartupListener implements ServletContextListener {
	private Logger log = Logger.getLogger(getClass().getName());
	private AtomicInteger threadNumber = new AtomicInteger(0);
	private static HypeCycleBot bot = new HypeCycleBot();
	private boolean startBot = true;
	private ScheduledExecutorService executor;
	
	@Override
	public void contextDestroyed(ServletContextEvent ev) {
		log.info("Context stopping...");
		Observable.from(executor.submit(() -> {
			try {
				bot.stop();
				bot = null;
			} catch (Exception e) {
				log.log(Level.WARNING, "Failed to stop: ", e);
			}
		})).retry(5).toBlocking().first();
		
		executor.shutdown();
		executor.shutdownNow();
	}
	
	@Override
	public void contextInitialized(ServletContextEvent ev) {
		log.info("Context starting...");
		
		int maxThreads = 5;
		executor = Executors.newScheduledThreadPool(maxThreads, (Runnable r) -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName(bot.getClass().getSimpleName() + "-" + threadNumber.incrementAndGet());
			if (t.getPriority() != Thread.NORM_PRIORITY) {
				t.setPriority(Thread.NORM_PRIORITY);
			}
			return t;
		});
		
		ServletContext servletContext = ev != null ? ev.getServletContext() : null;
		
		if (servletContext != null) {
			bot.setServletContext(servletContext);
		}

		if (startBot) {
			Observable.from(executor.submit(() -> {
				log.info("Starting " + bot);
				try {
					bot.start();
				} catch (Exception e) {
					log.log(Level.WARNING, "Failed to start", e);
				}
			})).retry(5).toBlocking().last();
		}
	}
	
	public static void main(String[] args) throws InterruptedException {
		StartupListener l = new StartupListener();
		l.contextInitialized(null);
		while (l.bot.isStarted()) {
			Thread.yield();
			Thread.sleep(200);
		}
		// l.contextDestroyed(null);
	}
}
