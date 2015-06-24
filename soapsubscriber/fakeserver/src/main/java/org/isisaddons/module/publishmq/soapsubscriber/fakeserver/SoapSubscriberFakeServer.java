
/**
 * Please modify this class to meet your needs
 * This class is not complete
 */

package org.isisaddons.module.publishmq.soapsubscriber.fakeserver;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.Holder;

import org.isisaddons.module.publishmq.soapsubscriber.demoobject.DemoObject;
import org.isisaddons.module.publishmq.soapsubscriber.demoobject.PostResponse;
import org.isisaddons.module.publishmq.soapsubscriber.demoobject.Update;

@WebService(
                      serviceName = "DemoObjectService",
                      portName = "DemoObjectOverSOAP",
                      targetNamespace = "http://isisaddons.org/module/publishmq/soapsubscriber/DemoObject/",
                      wsdlLocation = "classpath:DemoObject.wsdl",
                      endpointInterface = "org.isisaddons.module.publishmq.soapsubscriber.demoobject.DemoObject")

public class SoapSubscriberFakeServer implements DemoObject {

    private static final Logger LOG = Logger.getLogger(SoapSubscriberFakeServer.class.getName());

    private List<Update> updates = new ArrayList<>();

    @Override public void get(
            @WebParam(mode = WebParam.Mode.INOUT, name = "internalId", targetNamespace = "") final Holder<String> internalId, @WebParam(mode = WebParam.Mode.OUT, name = "update", targetNamespace = "") final Holder<Update> update) {
        update.value = updates.get(Integer.parseInt(internalId.value));
    }

    public org.isisaddons.module.publishmq.soapsubscriber.demoobject.PostResponse post(Update update) {
        LOG.info("Executing operation processed");
        System.out.println(update);
        try {
            updates.add(update);

            PostResponse response = new PostResponse();
            response.setInternalId(updates.size());
            response.setMessageId(update.getMessageId());

            return response;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }

}