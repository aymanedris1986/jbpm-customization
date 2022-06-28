FROM  quay.io/kiegroup/jbpm-server-full
ADD ./opt/jboss/wildfly/standalone/configuration/standalone.xml /opt/jboss/wildfly/standalone/configuration/
ADD ./opt/jboss/wildfly/standalone/deployments/business-central.war /opt/jboss/wildfly/standalone/deployments/
ADD ./opt/jboss/wildfly/standalone/deployments/jbpm-casemgmt.war /opt/jboss/wildfly/standalone/deployments/
ADD ./opt/jboss/wildfly/standalone/deployments/kie-server.war /opt/jboss/wildfly/standalone/deployments/