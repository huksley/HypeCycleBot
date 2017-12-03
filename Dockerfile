FROM maven:alpine

RUN apk --update add git && \
	git clone https://github.com/huksley/HypeCycleBot && \
	cd HypeCycleBot && \
	mvn clean package

FROM tomcat:8.5
COPY --from=0 /HypeCycleBot/target/HypeCycleBot /usr/local/tomcat/webapps/HypeCycleBot

RUN groupadd tomcat && \
	useradd -ms /bin/bash -g tomcat tomcat && \
	chown tomcat.tomcat /usr/local/tomcat -R

USER tomcat

RUN true &&\
	chmod a+rX /usr/local/tomcat -R && \
	chmod a+x /usr/local/tomcat/bin/catalina.sh && \
	chmod a+rw /usr/local/tomcat/temp && \
	rm -Rf /usr/local/tomcat/webapps/docs && \
	rm -Rf /usr/local/tomcat/webapps/examples && \
	rm -Rf /usr/local/tomcat/webapps/host-manager && \
	rm -Rf /usr/local/tomcat/webapps/manager && \
	rm -Rf /usr/local/tomcat/webapps/ROOT && \
	mkdir /usr/local/tomcat/webapps/HypeCycleBot/img && \
	chmod a+rwX /usr/local/tomcat/webapps/HypeCycleBot/img

WORKDIR /usr/local/tomcat

