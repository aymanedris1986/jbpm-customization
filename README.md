<h3>Customization of JBPM docker image that works with database security and helper custom library</h3>
<br/>
<br/>
docker build --tag jbpm-customization:latest .
<br/>
<br/>
docker run -p 8080:8080 -p 8001:8001 -d --name jbpm-customization -e JBPM_DB_DRIVER=postgres -e JBPM_DB_HOST=10.3.2.44 jbpm-customization:latest
<br/>
<br/>
working paths
/opt/jboss/wildfly/standalone/configuration/standalone-full.xml
