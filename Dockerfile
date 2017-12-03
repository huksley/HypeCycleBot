FROM maven:alpine

RUN apk --update add git && \
	git clone https://github.com/huksley/HypeCycleBot && \
	cd HypeCycleBot && \
	mvn clean package

FROM tomcat:8.5
COPY --from=0 /HypeCycleBot/target/HypeCycleBot.war /usr/local/tomcat/webapps/HypeCycleBot.war

RUN groupadd tomcat && useradd -ms /bin/bash -g tomcat tomcat && \
	chown tomcat.tomcat /usr/local/tomcat -R 

USER tomcat
WORKDIR /usr/local/tomcat
