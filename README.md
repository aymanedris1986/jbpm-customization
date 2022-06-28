docker build --tag jbpm-customization:latest .
docker run -p 8080:8080 -p 8001:8001 -d --name jbpm-customization -e JBPM_DB_DRIVER=postgres -e JBPM_DB_HOST=10.3.2.44 jbpm-customization:latest

working paths
/opt/jboss/wildfly/standalone/configuration/standalone-full.xml