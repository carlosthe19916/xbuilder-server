package org.openublpe.xmlbuilder.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helger.ubl21.UBL21Reader;
import io.github.carlosthe19916.webservices.managers.BillServiceManager;
import io.github.carlosthe19916.webservices.providers.BillServiceModel;
import io.github.carlosthe19916.webservices.wrappers.ServiceConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.debitnote_21.DebitNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openublpe.xmlbuilder.data.CreditNoteInputGenerator;
import org.openublpe.xmlbuilder.data.DebitNoteInputGenerator;
import org.openublpe.xmlbuilder.data.InvoiceInputGenerator;
import org.openublpe.xmlbuilder.models.input.general.invoice.InvoiceInputModel;
import org.openublpe.xmlbuilder.models.input.general.note.creditNote.CreditNoteInputModel;
import org.openublpe.xmlbuilder.models.input.general.note.debitNote.DebitNoteInputModel;
import org.openublpe.xmlbuilder.utils.CertificateDetails;
import org.openublpe.xmlbuilder.utils.CertificateDetailsFactory;
import org.openublpe.xmlbuilder.utils.XMLSigner;
import org.openublpe.xmlbuilder.utils.XMLUtils;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class DocumentsResourceTest {

    static String SIGN_REFERENCE_ID = "SIGN-ID";

    static String KEYSTORE = "LLAMA-PE-CERTIFICADO-DEMO-10467793549.pfx";
    static String KEYSTORE_PASSWORD = "password";
    static CertificateDetails CERTIFICATE;

    static final String SUNAT_BETA_URL = "https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService";
    static final String SUNAT_BETA_USERNAME = "MODDATOS";
    static final String SUNAT_BETA_PASSWORD = "MODDATOS";

    static List<InvoiceInputModel> invoiceInputs = new ArrayList<>();
    static List<CreditNoteInputModel> creditNoteInputs = new ArrayList<>();
    static List<DebitNoteInputModel> debitNoteInputs = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() throws UnrecoverableEntryException, NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {
        InputStream ksInputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(KEYSTORE);
        CERTIFICATE = CertificateDetailsFactory.create(ksInputStream, KEYSTORE_PASSWORD);

        ServiceLoader<InvoiceInputGenerator> serviceLoader1 = ServiceLoader.load(InvoiceInputGenerator.class);
        for (InvoiceInputGenerator generator : serviceLoader1) {
            invoiceInputs.add(generator.getInvoice());
        }

        ServiceLoader<CreditNoteInputGenerator> serviceLoader2 = ServiceLoader.load(CreditNoteInputGenerator.class);
        for (CreditNoteInputGenerator generator : serviceLoader2) {
            creditNoteInputs.add(generator.getCreditNote());
        }

        ServiceLoader<DebitNoteInputGenerator> serviceLoader3 = ServiceLoader.load(DebitNoteInputGenerator.class);
        for (DebitNoteInputGenerator generator : serviceLoader3) {
            debitNoteInputs.add(generator.getDebitNote());
        }
    }

    @Test
    public void testCreateInvoice() throws Exception {
        for (InvoiceInputModel input : invoiceInputs) {
            // Given
            String body = new ObjectMapper().writeValueAsString(input);

            // When
            Response response = given()
                    .body(body)
                    .header("Content-Type", "application/json")
                    .when()
                    .post("/documents/invoice/create")
                    .thenReturn();

            // Then
            assertEquals(200, response.getStatusCode());
            InputStream bodyInputStream = response.getBody().asInputStream();

            // read document
            Document xmlDocument = XMLUtils.inputStreamToDocument(bodyInputStream);
            assertNotNull(xmlDocument);

            // Sign document
            Document xmlSignedDocument = XMLSigner.firmarXML(xmlDocument, SIGN_REFERENCE_ID, CERTIFICATE.getX509Certificate(), CERTIFICATE.getPrivateKey());

            // Validate valid XML
            InvoiceType invoiceType = UBL21Reader.invoice().read(xmlSignedDocument);
            assertNotNull(invoiceType);

            // Send to test
            final ServiceConfig config = new ServiceConfig.Builder()
                    .url(SUNAT_BETA_URL)
                    .username(input.getProveedor().getRuc() + SUNAT_BETA_USERNAME)
                    .password(SUNAT_BETA_PASSWORD)
                    .build();
            String invoiceFileNameWithoutExtension = XMLUtils.getInvoiceFileName(input.getProveedor().getRuc(), input.getSerie(), input.getNumero());
            byte[] bytes = XMLUtils.documentToBytes(xmlSignedDocument);
            BillServiceModel billServiceModel = BillServiceManager.sendBill(invoiceFileNameWithoutExtension + ".xml", bytes, config);
            assertEquals(billServiceModel.getStatus(), BillServiceModel.Status.ACEPTADO, billServiceModel.getCode() + ":" + billServiceModel.getDescription());
        }
    }

    @Test
    public void testCreateCreditNote() throws Exception {
        for (CreditNoteInputModel input : creditNoteInputs) {
            // Given
            String body = new ObjectMapper().writeValueAsString(input);

            // When
            Response response = given()
                    .body(body)
                    .header("Content-Type", "application/json")
                    .when()
                    .post("/documents/credit-note/create")
                    .thenReturn();

            // Then
            assertEquals(200, response.getStatusCode());
            InputStream bodyInputStream = response.getBody().asInputStream();

            // read document
            Document xmlDocument = XMLUtils.inputStreamToDocument(bodyInputStream);
            assertNotNull(xmlDocument);

            // Sign document
            Document xmlSignedDocument = XMLSigner.firmarXML(xmlDocument, SIGN_REFERENCE_ID, CERTIFICATE.getX509Certificate(), CERTIFICATE.getPrivateKey());

            // Validate valid XML
            CreditNoteType creditNoteType = UBL21Reader.creditNote().read(xmlSignedDocument);
            assertNotNull(creditNoteType);

            // Send to test
            final ServiceConfig config = new ServiceConfig.Builder()
                    .url(SUNAT_BETA_URL)
                    .username(input.getProveedor().getRuc() + SUNAT_BETA_USERNAME)
                    .password(SUNAT_BETA_PASSWORD)
                    .build();
            String invoiceFileNameWithoutExtension = XMLUtils.getNotaCredito(input.getProveedor().getRuc(), input.getSerie(), input.getNumero());
            byte[] bytes = XMLUtils.documentToBytes(xmlSignedDocument);
            BillServiceModel billServiceModel = BillServiceManager.sendBill(invoiceFileNameWithoutExtension + ".xml", bytes, config);
            assertEquals(billServiceModel.getStatus(), BillServiceModel.Status.ACEPTADO, billServiceModel.getCode() + ":" + billServiceModel.getDescription());
        }
    }

    @Test
    public void testCreateDebitNote() throws Exception {
        for (DebitNoteInputModel input : debitNoteInputs) {
            // Given
            String body = new ObjectMapper().writeValueAsString(input);

            // Then
            Response response = given()
                    .body(body)
                    .header("Content-Type", "application/json")
                    .when()
                    .post("/documents/debit-note/create")
                    .thenReturn();

            // Then
            assertEquals(200, response.getStatusCode());
            InputStream bodyInputStream = response.getBody().asInputStream();

            // read document
            Document xmlDocument = XMLUtils.inputStreamToDocument(bodyInputStream);
            assertNotNull(xmlDocument);

            // Sign document
            Document xmlSignedDocument = XMLSigner.firmarXML(xmlDocument, SIGN_REFERENCE_ID, CERTIFICATE.getX509Certificate(), CERTIFICATE.getPrivateKey());

            // Validate valid XML
            DebitNoteType debitNoteType = UBL21Reader.debitNote().read(xmlSignedDocument);
            assertNotNull(debitNoteType);

            // Send to test
            final ServiceConfig config = new ServiceConfig.Builder()
                    .url(SUNAT_BETA_URL)
                    .username(input.getProveedor().getRuc() + SUNAT_BETA_USERNAME)
                    .password(SUNAT_BETA_PASSWORD)
                    .build();
            String invoiceFileNameWithoutExtension = XMLUtils.getNotaDebito(input.getProveedor().getRuc(), input.getSerie(), input.getNumero());
            byte[] bytes = XMLUtils.documentToBytes(xmlSignedDocument);
            BillServiceModel billServiceModel = BillServiceManager.sendBill(invoiceFileNameWithoutExtension + ".xml", bytes, config);
            assertEquals(billServiceModel.getStatus(), BillServiceModel.Status.ACEPTADO, billServiceModel.getCode() + ":" + billServiceModel.getDescription());
        }
    }

}