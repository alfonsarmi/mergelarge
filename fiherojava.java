

package jfactory.sirija.servlet;
surnetcomun/src/main/java/jfactory/sirija/servlet/PresentacionSimplificadaServlet.java
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.xml.sax.SAXException;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfAction;
import com.lowagie.text.pdf.PdfWriter;

import es.juntadeandalucia.economiayhacienda.clienteAFirma5.ClienteFirma;
import es.juntadeandalucia.economiayhacienda.clienteAFirma5.ClienteFirmaFactory;
import es.juntadeandalucia.economiayhacienda.clienteAFirma5.FirmaException;
import es.juntadeandalucia.economiayhacienda.clienteAFirma5.respuesta.InformacionDatosFirmados;
import es.juntadeandalucia.economiayhacienda.clienteAFirma5.respuesta.InformacionFirmante;
import es.juntadeandalucia.economiayhacienda.clienteAFirma5.utils.Base64;
import es.juntadeandalucia.economiayhacienda.clienteAFirma5.utils.DatosAFirmar;
import es.juntadeandalucia.economiayhacienda.comun.fielato.CertificadoDigital;
import es.juntadeandalucia.economiayhacienda.generacion.modpdf.ModificacionPDF;
import es.juntadeandalucia.economiayhacienda.modelos.comun.Casilla;
import es.juntadeandalucia.economiayhacienda.serviciosWeb.tasas.DocumentoRespuesta;
import es.juntadeandalucia.economiayhacienda.suriutil.VerificadorProcedenciaCliente;
import jfactory.ejb.PortableContext;
import jfactory.exchange.ResultInfo;
import jfactory.igu.jsp.ComboInfo;
import jfactory.jbean.PropApplication;
import jfactory.sirija.ejb.bl.CryptoBLImpl;
import jfactory.servlet.BaseServletController;
import jfactory.sirija.accesoExterno.Utilidades;
import jfactory.sirija.admin.jbean.SessionAdminJBean;
import jfactory.sirija.jbean.NumeroSURWebJBean;
import jfactory.sirija.jbean.SessionJBean;
import jfactory.sirija.sa.AutorizacionInfo;
import jfactory.sirija.sa.CasillaInfo;
import jfactory.sirija.sa.CasillaInfoSet;
import jfactory.sirija.sa.CensoRespInfo;
import jfactory.sirija.sa.CifradoInfo;
import jfactory.sirija.sa.CodigoTerritorialInfo;
import jfactory.sirija.sa.CodigoTerritorialInfoSet;
import jfactory.sirija.sa.ConsultaLiquidacionInfo;
import jfactory.sirija.sa.ContratoInfo;
import jfactory.sirija.sa.CuentaBancariaAutorizacionInfo;
import jfactory.sirija.sa.DocumentoAdjuntoInfo;
import jfactory.sirija.sa.DocumentoAdjuntoInfoSet;
import jfactory.sirija.sa.EntidadFinancieraInfo;
import jfactory.sirija.sa.EntidadFinancieraInfoSet;
import jfactory.sirija.sa.EvolucionLiquidacionInfo;
import jfactory.sirija.sa.FicheroAdjuntoInfo;
import jfactory.sirija.sa.LiquidacionInfo;
import jfactory.sirija.sa.LiquidacionPlusInfo;
import jfactory.sirija.sa.ModeloAmpliadoInfo;
import jfactory.sirija.sa.ModeloAmpliadoInfoSet;
import jfactory.sirija.sa.NumeroSURWebPetInfo;
import jfactory.sirija.sa.NumeroSURWebRespInfo;
import jfactory.sirija.sa.PresentacionInfo;
import jfactory.sirija.sa.RespuestaPagoInfo;
import jfactory.sirija.sa.TipoEstadoLiquidacionInfo;
import jfactory.sirija.sa.UserProfileInfo;
import jfactory.sirija.util.ApplicationError;
import jfactory.sirija.util.Constantes;
import jfactory.sirija.util.ControladorSimulador;
import jfactory.sirija.util.Presentacion;
import jfactory.sirija.util.Utiles;
import jfactory.sirija.util.ValidacionRequest;
import jfactory.util.JFactoryException;
import jfactory.util.Propiedades;
import jfactory.util.Text;

/** 
 * 
 *
 */  
public class PresentacionSimplificadaServlet extends BaseServlet {
    
    private static String entorno = System.getProperty("entorno");
    private static Log logger = LogFactory.getLog(PresentacionSimplificadaServlet.class);
    private static final long serialVersionUID = 1L;

    
    public void init() {
        super.init("PresentacionSimplificada");
    }

    /**
    *  Method to manage actions
    */
    public void ejecutar(HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
        
        String urlDst = "";
        
        //Variable que se encarga para que la funci�n AJAX no realize el forward y no muestre error en el log ya que es asincrono.
        boolean adjuntoActualizacion = false;
        try {
            //Cargo un userProfile con el idioma por defecto para mostrar el mensaje de sesion expirada
            String idIdioma = Utiles.getPropiedadesSiri().getString(Constantes.IDIOMA);
            
            UserProfileInfo userProfile = new UserProfileInfo();
            userProfile.setIdIdioma(idIdioma);
            ResultInfo resultInfo = new ResultInfo(true,Constantes.LITERAL_SESION_EXPIRADA,Constantes.MENSAJE_SESION_EXPIRADA);
            
            request.setAttribute("destino", Utiles.getPathSiriAction() + "Session?" + BaseServletController.OPERATION + "="+ Constantes.ACCION_LOGIN);
            request.setAttribute("resultInfo", resultInfo);
            
            if (checkSessionExpired(request, response, "sessionJBean")){
                 return;
            } else {
                request.removeAttribute("destino");
                request.removeAttribute("resultInfo");
            }

            duplicateIfCtrlN(request); 
            
            String accion = request.getParameter(BaseServletController.OPERATION);
            logger.debug("accion: " + accion);

            userProfile = getUserProfile(request);
            
            //Mostrar la pagina para el inicio de la presentacion simplificada
            if (Constantes.PRESENTACION_SIMPLIFICADA.equals(accion)){
                if (userProfile.getIdTipoContrato()==Constantes.ID_TIPO_FUNCIONARIO)
                    urlDst=inicioPresentacionFuncionario(request,userProfile);
                else
                    urlDst = inicioPresentacionSimplificada(request, userProfile);
                if (urlDst == null) return;  //Se devolvi� ya el PDF
                
            // MOSTRAR EL PDF PARA FIRMARLO
            } else if (Constantes.ACCION_DESC_ID_FIRMA.equals(accion)){
                descargaIdFirma(request, response, userProfile);
                return; 
            } else if (Constantes.ACCION_IMPRIMIR_AUTO.equals(accion)) {
            	generaAutorizacionImpresion(request, response, userProfile);
                return; 
            //Se devolvi� ya el PDF
            } else if (Constantes.INICIO_PRESENTACION_SIMPLIFICADA_SIN_FIRMA.equals(accion)){
                urlDst = inicioPresentacionSimplificadaSinFirma(request, userProfile);
                if (urlDst == null) return;  //Se devolvi� ya el PDF
            //Se devolvi� ya el PDF
            }else if (Constantes.PAGO_PRESENTACION.equals(accion)){
            	// Colocamos en sesi�n un atributo que indica que se est� realizando el pago presentaci�n
            	request.getSession().setAttribute("pagoBloqueo", "true");
                urlDst = pagoPresentacion(request, userProfile);
                if (urlDst == null) return;  //Se devolvi� ya el PDF
            //Se devolvi� ya el PDF
            } else if (Constantes.DATOS_FUNCIONARIO.equals(accion)){
                urlDst=datosFuncionario(request, response, userProfile);
                
            // COMIENZO DEL PROCESO DE FIRMA,PAGO y PRESENTACION
            } else if (Constantes.FIRMA_PAGO_PRESENTACION.equals(accion)) {
            	// Colocamos en sesi�n un atributo que indica que se est� realizando el pago presentaci�n
            	request.getSession().setAttribute("pagoBloqueo", "true");
                urlDst= firmaPagoPresentacion(request, userProfile);
            // COMIENZO DEL PROCESO FIRMA, PAGO (CONTRA IECISA) Y PRESENTACION 
            } else if (Constantes.INICIO_FIRMA_PAGO_TARJETA.equals(accion)) {
            	// Colocamos en sesi�n un atributo que indica que se est� realizando el pago presentaci�n
            	request.getSession().setAttribute("pagoBloqueo", "true");
                urlDst= inicioFirmaPagoTarjeta(request, userProfile);
            //RESULTADO DEL PROCESO FIRMA, PAGO (CONTRA IECISA) Y PRESENTACION 
            } else if (Constantes.MOSTRAR_RESULTADO_PAGO_TARJETA.equals(accion)) {
                urlDst= mostrarResultadoFirmaPagoTarjeta(request, userProfile); 
            } else if (Constantes.INFORMACION.equals(accion)) {
                urlDst= informacion(request, userProfile);
            
            //ACCESO A SURNET DESDE PP_SIMPLIFICADO
            } else if (Constantes.ACCESO_SURNET.equals(accion)){
                urlDst= accesoSURNET(request, userProfile);
            } else if (Constantes.SALIR_APLICACION.equals(accion)){
                urlDst= salirAplicacion(request, userProfile);
            //NUEVO PROCESO DE PRESENTACION/CONTINUACION DE PAGO TPV PINPAD FUNCIONARIO
            } else if (Constantes.PRESENTACION_TPV_PINPAD_FUNCIONARIO.equals(accion)){
                urlDst= presentacionTPVPinPadFuncionario(request, userProfile);
            } else if (Constantes.ELIMINAR_DOCUMENTO_SALIR.equals(accion)){
            	// Proceso de eliminaci�n de un documento simplificado
            	urlDst = peticionSalir(request, userProfile);
            } else if (Constantes.ADJUNTAR_DOCUMENTACION.equals(accion)){
            	urlDst = adjuntarDocumentacionSimplificada(request, userProfile);
            } else if (Constantes.CONTINUAR_PROCESO_ADJUNTOS_SIMPLIFICADA.equals(accion)){
            	urlDst = continuarProcesoAdjuntosSimplificada(request, userProfile);
            } else if (Constantes.CONTINUAR_SIN_ADJUNTAR.equals(accion)){
            	urlDst = continuarSimplificadaSinAdjuntar (request, userProfile);
            } else if (Constantes.CONTINUAR_CON_ADJUNTAR.equals(accion)){
            	urlDst = continuarSimplificadaConAdjuntar (request, userProfile);
            } // Proceso de firma del fichero (762) (PASO 3)
			  else if (Constantes.ACCION_FIRMA_FICHERO_ADJUNTO.equals(accion)) {
				urlDst= firmarFicheroAdjunto(request, userProfile);
			} else if(Constantes.ACTUALIZAR_ADJUNTO_VALIDACION.equals(accion)){
				String estado = request.getParameter("estado");
				String docAdjunto = request.getParameter("docAdjunto");
				adjuntoActualizacion = true;
				if(docAdjunto != null && estado != null){
					long estadoLong = Long.valueOf(estado);
					actualizarAdjuntoValidacion(request, userProfile, estadoLong, docAdjunto);
				}else{
					logger.error("No ha sido posible actualizar el documento adjunto: " + docAdjunto + ", al estado: " + estado);
				}	
			}
            
            //FIXME: Creemos que no sirve, pero lo comento por si realmente era necesario
            //borraVariablesSesion(request,userProfile);
            // PAGINACI�N ----------------------------------------------------------------------------------------------------
            urlDst = urlDst + "?idWindow=" + strWindow;
            // ---------------------------------------------------------------------------------------------------------------
            
            if(request.getSession().getAttribute("accesoExterno") != null && request.getSession().getAttribute("accesoExterno").equals("true") && urlDst.equals(getUrl(Constantes.MOSTRAR_ERROR + "?idWindow=" + strWindow))
            		|| (null != request.getSession().getAttribute("accesoPlages") && request.getSession().getAttribute("accesoPlages").equals("true") && urlDst.equals(getUrl(Constantes.MOSTRAR_ERROR + "?idWindow=" + strWindow)))){
                PresentacionInfo presInfo = (PresentacionInfo)request.getAttribute("presentacionInfoError");
                logger.debug("Informando del error a la aplicacion externa: "+presInfo.getNombreAplicacion());
                boolean hayUrlRecibo = devolverUrlRecibo(request, userProfile, presInfo);                
                if(hayUrlRecibo){
                	if (null != presInfo.getNombreAplicacion() && presInfo.getNombreAplicacion().equals("GESTORIA") 
                			&& (userProfile.getIdTipoContrato() == Constantes.ID_TIPO_FUNCIONARIO || userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO_R40)){
                		//Como hay urlRecibo, redirecciono
    					if(request.getAttribute("urlRecibo") != null){
    						logger.debug("Redirecciono a la url: " + (String)request.getAttribute("urlRecibo"));
    						request.setAttribute("aplicacion","GESTORIA");
    						urlDst = getUrl(Constantes.URL_RECIBO_TARJETA_FRAME);
    					}else{
    						throw new Exception("No existe url de recibo");
    					}
                	}else{
	                    //Creacion del String encriptado a partir del DocRespuesta que viene del jar de tasas
	                    DocumentoRespuesta docRes = new DocumentoRespuesta();
	                    docRes.setNumeroDocumento(presInfo.getIdLiquidacion());
	                    Date fechaIng = presInfo.getFechaPago();
	                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd/MM/yyyy");
	                    if(fechaIng != null){
	                        String fechaIngP = simpleDateFormat.format(fechaIng);
	                        docRes.setFechaIngreso(fechaIngP);
	                    }
	                    docRes.setEstado("ERROR");
	                    docRes.setError("El proceso de pago/presentaci�n ha fallado");
	                    docRes.setReferenciaExterna((String)request.getSession().getAttribute("referenciaExterna"));
	                    
	                    String xmlEncriptado = obtenerDocResEncriptado(request,docRes,presInfo.getNombreAplicacion());
	                    if(xmlEncriptado != null){
	                        request.setAttribute("datosEncriptados", xmlEncriptado);
	                        urlDst = getUrl(Constantes.URL_RECIBO);
	                    }else{
	                        logger.error("Ha habido un error al intentar encriptar el docRespuesta que debemos de enviar para la aplicacion: " + presInfo.getNombreAplicacion());
	                        request.setAttribute("userProfile",getUserProfile(request));
	                        urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	                    }  
                	}                    
                }
            }
        } catch (Exception e){        	
            logger.error(e.getMessage(), e);            
            request.setAttribute("servletDestino", request.getParameter("servletCreador"));
            request.setAttribute("accionCreador", request.getParameter(BaseServletController.OPERATION));
            request.setAttribute("userProfile",getUserProfile(request));
            urlDst=getUrl(Constantes.MOSTRAR_ERROR);
        } catch (ApplicationError ae) {
            logger.error(ae.getResultInfo()!=null ? ae.getResultInfo().getMessage(): ae.getMessage(), ae);

            // Preparar datos para crash.jsp
            try{
                request.setAttribute("destino", Utiles.getPathSiriAction() + "Session?" + BaseServletController.OPERATION + "="+ Constantes.ACCION_LOGIN);
            }catch(Exception e){}
            request.setAttribute("servletDestino", request.getParameter("servletCreador"));
            request.setAttribute("accionCreador", request.getParameter(BaseServletController.OPERATION));
            request.setAttribute("resultInfo", ae.getResultInfo());
            request.setAttribute("userProfile",getUserProfile(request));
            urlDst=getUrl(Constantes.MOSTRAR_ERROR);
        }
        
        if(!adjuntoActualizacion){
        	forward(request, response, urlDst);
        }else{
        	String estado = request.getParameter("estado");
			String docAdjunto = request.getParameter("docAdjunto");
        	logger.debug("No realiza el forward de la url: " + urlDst + " , ya que viene de una petici�n AJAX con los parametros: ESTADO: " + estado + " y DOCUMENTO ADJUNTO: " + docAdjunto); 
        }
        
        //RequestDispatcher rd = request.getRequestDispatcher(urlDst);
        //rd.forward(request, response);
               
    }
    
	private byte[] generaPDFFuncionario(HttpServletRequest request, UserProfileInfo userProfile, 
            ConsultaLiquidacionInfo consultaLiquidacionInfo) 
        throws Exception {
        byte[] oPDF = null;
        try{
            //String idLiquidacion = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
            String[] idsDocumentosFirmar = new String[1];
            idsDocumentosFirmar[0] = consultaLiquidacionInfo.getIdLiquidacion();        
            String sReferencia = consultaLiquidacionInfo.getReferencia();
            String sEstado = Long.toString(consultaLiquidacionInfo.getIdEstadoActual());
            String sXML = null;
            sXML = getLiquidacionBLJBean(request).generaXML(userProfile, idsDocumentosFirmar, 
                sReferencia, Integer.parseInt(sEstado));
            sXML = Utiles.cifra(sXML.getBytes(Constantes.ENCODING_XML_ISO));
            oPDF = Utiles.generaPDF(sXML);
            if (oPDF.length == 0) {
                //Se produjo un error en la generacion del PDF.
                throw new ApplicationError(new ResultInfo(true, "PDF010", 
                    getSessionJBean(request).getLiteral("PDF010")), 
                    new Exception("Se produjo un error en el servidor de PDFs"));
            }
        }catch(Exception e){
            logger.error("Error al generar el PDF de Funcionario: " + e.toString());
            throw e;
        }
        return oPDF;
}
    
    /** GENERA EL PDF **/
    
    private byte[] generaPDF(HttpServletRequest request, UserProfileInfo userProfile) 
        throws Exception {
    
        //String idLiquidacion = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
        String[] idsDocumentosFirmar = new String[1];
        idsDocumentosFirmar[0] = (String)request.getAttribute("idAutoliquidacion");        
        String sReferencia = (String)request.getAttribute(Constantes.PDF_REFERENCIA);
        String sEstado = (String)request.getAttribute(Constantes.PDF_ESTADO);
        String sXML = null;
        byte[] oPDF = null;

        sXML = getLiquidacionBLJBean(request).generaXML(userProfile, idsDocumentosFirmar, 
            sReferencia, Integer.parseInt(sEstado));
        sXML = Utiles.cifra(sXML.getBytes(Constantes.ENCODING_XML_ISO));
        
        //Nueva distincion para que se muestre una marca de agua si el estado del documento es no final
        int idEstado = Integer.parseInt(sEstado);
        if ((idEstado != Constantes.ESTADO_PRESENTADO) && (idEstado != Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR) && (idEstado != Constantes.ESTADO_PAGADO) && (idEstado != Constantes.ESTADO_ERROR_NRC) ) {
            oPDF = Utiles.generaPDFMarca(sXML);
        }else{
            oPDF = Utiles.generaPDF(sXML);
        }   

        if (oPDF.length == 0) {
            //Se produjo un error en la generacion del PDF.
            throw new ApplicationError(new ResultInfo(true, "PDF010", 
                getSessionJBean(request).getLiteral("PDF010")), 
                new Exception("Se produjo un error en el servidor de PDFs"));
        }
        return oPDF;
    }

    /** MUESTRA EL PDF GENERADO POR EL NAVEGADOR **/
    
    private void enviaFichero(byte[] fichero, HttpServletResponse response, String idLiquidacion) throws Exception{

        if (fichero[0] == '<') {
            //He generado un XML
            response.setContentType("application/xml");
            logger.trace("Envio un XML");
            ServletOutputStream outputStream = response.getOutputStream();
            outputStream.write (fichero, 0, fichero.length);
            outputStream.flush();
        } else {
            //He generado un PDF
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition","inline; filename=\""+idLiquidacion+".pdf\";");
            response.setHeader("Expires", "0");
            response.setHeader("Cache-Control","max-age=0,s-maxage=0,must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Pragma", "public");
            response.setHeader("Pragma", "public");           
            response.setContentLength(fichero.length);
            response.getOutputStream().write(fichero);
            response.flushBuffer();
           
            logger.trace("Envio un PDF");
        }
      
        
    }
 
    private byte[] modificarPDFAutorizacionImpresion(byte[] fichero, String idLiquidacion) throws DocumentException, IOException{
        
        byte[] ficheroModificado = null;
        Document document = new Document();
        try{
            Propiedades prop = Utiles.getPropiedadesSiri();
            String path = getServletContext().getRealPath("");
            OutputStream out = new FileOutputStream(path+prop.getString(Constantes.UPLOAD_DIRNAME)+"/"+idLiquidacion+".pdf"); 
            out.write(fichero); 
            out.close();         
            
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfAction action = new PdfAction(PdfAction.PRINTDIALOG);
            writer.setOpenAction(action);
            
            ficheroModificado = out.toString().getBytes(Charset.forName("UTF-8"));
            
            
            
        }catch(Exception e){
            logger.error("Error al modificar la version imprimible de la autorizaci�n: " +e.getMessage(),e);
        }
        
        
        return ficheroModificado;
    }
    
    /* Viejo que funcionaba pero no ponia el nombre. Recuperarlo si hay problemas
      private void enviaFichero(byte[] fichero, HttpServletResponse response, String idLiquidacion) throws Exception{

        if (fichero[0] == '<') {
            //He generado un XML
            response.setContentType("application/xml");
            getLog().writeTrace(Log.TRACE, this, "enviaFichero", "Envio un XML");
        } else {
            //He generado un PDF
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition","inline; filename="+idLiquidacion+".pdf;");
           
            getLog().writeTrace(Log.TRACE, this, "enviaFichero", "Envio un PDF");
        }
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write (fichero, 0, fichero.length);
        outputStream.flush();
        
    }*/

    /** UNA VEZ IMPORTADA LA AUTOLIQUIDACION WEB, 
     *  MUESTRA LA PAGINA CON EL PDF CORRESPONDIENTE 
     *  PARA INICIAR EL PROCESO SIMPLIFICADO
     ***/

    private String inicioPresentacionSimplificada(HttpServletRequest request, UserProfileInfo userProfile){
        
        String sEstado = (String)request.getAttribute(Constantes.PDF_ESTADO);
        //String idLiquidacion = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
        String idDocumentoFirmar = (String)request.getAttribute("idAutoliquidacion");

        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        String nombreDocumento = null;
        double idCustodia=0;
        String idTransaccionFirma="";
        HashMap<String, DatosAFirmar> listaIds = null;
        byte[] oPDF = null;
        DatosAFirmar sDatos = null;        
        ModeloAmpliadoInfo modeloAmpliadoInfo = new ModeloAmpliadoInfo();
        ConsultaLiquidacionInfo consultaLiquidacionInfo =  new ConsultaLiquidacionInfo();
        String mensajeDiligenciaPresentacion="";
        
        try{            
                                
            String idLiquidacion = idDocumentoFirmar;
               
            //Obtenemos la liquidacion para poder obtener su modelo correspondiente.
            LiquidacionInfo liquidacion = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
            modeloAmpliadoInfo = getPresentacionBLJBean(request).getModelo(userProfile, liquidacion.getIdModelo());

            //Comprobamos que el documento que se va a firmar no este firmado ya consultando su estado directamente de la base de datos.
            long estadoLiquidBD = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion).getIdEstadoActual(); 
            if (sEstado != null && !sEstado.equals(Long.toString(Constantes.ESTADO_PENDIENTE_FIRMA))) {
                logger.error("Se ha intentado firmar la liquidacion "+idLiquidacion+" en estado: " + sEstado);
                throw new ApplicationError(new ResultInfo(true, "PDF002", 
                getSessionJBean(request).getLiteral("PDF002")), new Exception("Se ha intentado firmar una liquidacion en estado: " + sEstado));                  
            }else if(estadoLiquidBD != Constantes.ESTADO_PENDIENTE_FIRMA){
                logger.error("Se ha intentado firmar la liquidacion "+idLiquidacion+" en estado: " + estadoLiquidBD);
                request.setAttribute("destino", Utiles.getPathSiriAction() + "Liquidacion?" + BaseServletController.OPERATION + "="+ Constantes.BUSCAR);
                throw new ApplicationError(new ResultInfo(true, "PDF002", 
                getSessionJBean(request).getLiteral("PDF002")), new Exception("Se ha intentado firmar una liquidacion en estado: " + estadoLiquidBD));          
            }else if(modeloAmpliadoInfo.getActivo() != Constantes.ID_ESTADO_CONTRATO_ACTIVO 
            		&&	modeloAmpliadoInfo.getActivo() != Constantes.ID_ESTADO_TABLA_ACTIVO_R40){ //Comprobamos que no este bloquedado el modelo
                logger.error("El modelo de la liquidacion "+idLiquidacion+"no esta activo");                        
                throw new ApplicationError(new ResultInfo(true, "ERR513", 
                getSessionJBean(request).getLiteral("ERR513")), 
                new Exception("El modelo no esta activo "));                                                      
               }                        
            //Obtengo el PDF
            oPDF = generaPDF(request, userProfile);            
            //Obtengo el nombre del fichero a partir del modelo, version y numDocumento de la autoliquidacion            
            LiquidacionInfo oLI = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idDocumentoFirmar);         
            
            //Obtengo si es el documento de TASAS para el bloqueo o no del pago con tarjeta
            request.getSession().setAttribute("aplicacion",oLI.getNombreAplicacion());
            
            nombreDocumento = oLI.getIdLiquidacion();
            
            try { 
                //Obtengo el objeto de cliente de Firma
                ClienteFirma clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA));

                //Registro el documento
                idCustodia = clienteFirma.registrarDocumento(oPDF, nombreDocumento, "PDF");
                //Genero los datos que tiene que firmar el usuario en el jsp
                sDatos = clienteFirma.generarDatosAFirmar(idCustodia);
                idTransaccionFirma = sDatos.getIdTransaccion();
            } catch (Exception e) {
                logger.error("Error en el inicio de presentacion Simplificada generando los datos a firmar: " + e);
                //Capturo cualquier excepci�n espec�fica del m�dulo de telventi
                throw new ApplicationError(new ResultInfo(true, "PDF009", 
                    getSessionJBean(request).getLiteral("PDF009")), e);
            }
            
            //Meto el id en la lista de ids guardados en sesion
            listaIds = (HashMap) request.getSession().getAttribute(Constantes.LISTA_IDS);
            if (listaIds == null) {
                listaIds = new HashMap<String, DatosAFirmar>();
            }
            listaIds.put(idTransaccionFirma, sDatos);
            request.getSession().setAttribute(Constantes.LISTA_IDS, listaIds);
            
            ArrayList<String> documentosFirmaMultiple = new ArrayList<String>();
            documentosFirmaMultiple.add(idDocumentoFirmar);
            request.getSession().setAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE, documentosFirmaMultiple);
                        
            request.setAttribute("idFirma", idTransaccionFirma);
            request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, idDocumentoFirmar);
            //request.getSession().setAttribute("pagoSimplificado","true");
            
            //Obtener las oficinas de presentacion y el combo con las entidades financieras.
            cargarDatosComboEntidadesFinancieras(request, userProfile);
            
            // Si estamos en pago diferido eliminamos las entidades financieras que no permitan el pago diferido
            boolean esPagoDiferido = liquidacion.isPagoDiferido();
            if (esPagoDiferido && userProfile.getIdTipoUsuario() != Constantes.ID_TIPO_FUNCIONARIO){
            	ArrayList arrayEntidades = null;
            	EntidadFinancieraInfoSet entidadInfoSet = null;
            	if ((userProfile.getIdTipoUsuario()==Constantes.ID_TIPO_FUNCIONARIO) || 
        				(String)request.getSession().getAttribute("pagoSimplificado")!=null){
            		entidadInfoSet = (EntidadFinancieraInfoSet)request.getSession().getAttribute("infoSetEntidadFinanciera");
            	}else{
        			entidadInfoSet =  (EntidadFinancieraInfoSet)request.getAttribute("infoSetEntidadFinanciera");
            	}
            	if (null != entidadInfoSet){
            		arrayEntidades = (ArrayList)entidadInfoSet.getArrayList();
            		// Si es pago con NRC diferido miro si existen cuentas bancarias que permitan el pago con NRC diferido
                	Iterator<EntidadFinancieraInfo> it = arrayEntidades.iterator();
                	while (it.hasNext()){
                		EntidadFinancieraInfo entidadInfo = (EntidadFinancieraInfo)it.next();
                		if (!entidadInfo.isPagoDiferido()){
                			// la elimino del combo
                			it.remove();
                		}
                	}
                	if (null == arrayEntidades || (null != arrayEntidades && arrayEntidades.size() < 1)){
                		request.setAttribute("resultInfo", new ResultInfo (true, null, "En estos momentos ninguna de las entidades financieras adscritas a la Plataforma Telem�tica de Pago admite el pago con NRC diferido. Disculpe las molestias."));
                		throw new Exception("Estamos en pago diferido y no existen endidades financieras que admitan pago diferido");
                	}else{
                		entidadInfoSet.setArrayList(arrayEntidades);
                		// almacenamos el nuevo array
                		if ((userProfile.getIdTipoUsuario()==Constantes.ID_TIPO_FUNCIONARIO) || 
                				(String)request.getSession().getAttribute("pagoSimplificado")!=null){
                    		request.getSession().setAttribute("infoSetEntidadFinancieraDiferido", entidadInfoSet);
                    	}else{
                			request.setAttribute("infoSetEntidadFinancieraDiferido", entidadInfoSet);
                    	}
                	}
            	}else{
            		request.setAttribute("resultInfo", new ResultInfo (true, null, "En estos momentos ninguna de las entidades financieras adscritas a la Plataforma Telem�tica de Pago admite el pago con NRC diferido. Disculpe las molestias."));
            		throw new Exception("Estamos en pago diferido y el combo de entidades financieras es nulo");
            	}
            }
            // vuelvo a cargar el combo de entidades financieras normales
            cargarDatosComboEntidadesFinancieras(request, userProfile);      
            
            //cargarDatosComboOFPresentacion(request, userProfile);
            ResultInfo resultInfo = getCombosOficinaPresentacion(request, userProfile, liquidacion);
            if (resultInfo.getError())
                throw new Exception();
            
            //GestionPermisos: Me trae la informaci�n relativa a ese documento
            consultaLiquidacionInfo = getLiquidacionBLJBean(request).getConsultaLiquidacion(userProfile,liquidacion);
            consultaLiquidacionInfo.setLiquidacionInfo909(liquidacion.getLiquidacionInfo909());
            //Lo eliminamos a petici�n al probar lo de los adjuntos del 600, nos indican que no quieren ver el mensaje este de diligencias.
            /*if(consultaLiquidacionInfo.getIdDiligencia().intValue()!=0){
                //Mostramos informacion acerca del estado de la diligencia de presentacion
                String aux = "00"+consultaLiquidacionInfo.getIdDiligencia();
                if (aux.length()>3){
                    //Quito el primer cero para tener 3digitos
                    aux = aux.substring(1);
                }
                mensajeDiligenciaPresentacion = getSessionJBean(request).getLiteral("DIL"+aux);

            }*/
            
            if(consultaLiquidacionInfo != null && consultaLiquidacionInfo.getResultInfo() != null
            		&& consultaLiquidacionInfo.getResultInfo().getMessage() != null 
            		&& consultaLiquidacionInfo.getResultInfo().getMessage().contains("modificada la Fecha de Primera Matriculaci�n")){
            	request.setAttribute("resultInfo",new ResultInfo(true,"","El pago/presentaci�n de autoliquidaciones en las que la fecha de primera matriculaci�n o potencia fiscal no se correspondan con los valores que constan en el Registro General de Veh�culos, est� restringido exclusivamente a Colaboradores Sociales / Profesionales con autorizaci�n vigente en esta Plataforma de Pago / Presentaci�n."));
            	getPresentacionBLJBean(request).deleteWebLiquidacionAndChildList(userProfile, idLiquidacion);
 				return getUrl(Constantes.MOSTRAR_ERROR);
            }
            
            request.getSession().setAttribute("mensajeDiligenciaPresentacion", mensajeDiligenciaPresentacion);            
             
            request.getSession().setAttribute("userProfile", userProfile); 
            request.getSession().setAttribute("liquidacionInfo", consultaLiquidacionInfo); 
           
            // Si el tipo de contrato y el tipo de usuario es funcionario entonces os vamos a la p�gina de expedir autorizaci�n
            // si no se cumple por ejemplo (Tipo de contrato EP R40 y tipo de usuario EP) vamos a presentaci�n simplificada normal
            if (userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO &&
            		userProfile.getIdTipoUsuario()==Constantes.ID_TIPO_FUNCIONARIO){            	
                urlDst = getUrl(Constantes.EXPEDIR_AUTORIZACION);          
            }else{
            	// Comprobamos que el usuario sea SIN CERTIFICADO, para poder adjuntar documentaci�n
               	if (userProfile.getIdUsuario() != 444){
	            	// Obtenemos los tipos de modelos que puede adjuntar para el modelo-version-concepto dados
	            	ModeloAmpliadoInfo modelo = getLiquidacionBLJBean(request).getModelo(userProfile, consultaLiquidacionInfo.getIdModelo());
	            	DocumentoAdjuntoInfoSet adjuntos_modadj = getLiquidacionBLJBean(request).getTiposDocumentosAdjuntos(consultaLiquidacionInfo.getIdLiquidacion(), modelo.getModelo(), modelo.getVersion(), consultaLiquidacionInfo.getConcepto());
	            	// Vemos si ya tiene adjuntado alg�n documento
	            	DocumentoAdjuntoInfoSet adjuntos_webliq = getLiquidacionBLJBean(request).getDocAdjuntosWebliq(consultaLiquidacionInfo.getIdLiquidacion());
	            	// Eliminamos si hab�a alg�n adjunto webliq en sesion y colocamos el nuevo
	        		request.getSession().removeAttribute("adjuntos_webliq");
	        		if (null != adjuntos_webliq){        			
	            		request.getSession().setAttribute("adjuntos_webliq", adjuntos_webliq);
	            		// Vemos si ya est�n todos correctos y lo guardo en sesi�n
	                	boolean estanCorrectos = getLiquidacionBLJBean(request).todosDocumentosAdjuntosCorrectos(adjuntos_modadj, adjuntos_webliq);
	                	request.getSession().setAttribute("adjuntosCorrectos", String.valueOf(estanCorrectos));
	            	}
	            	//Comprobamos si podemos adjuntar alg�n documento a este modelo_version_concepto y si el docuemnto esta pendiente de firma (estado=8).
	            	//En caso afirmativo mostramos la ventana para adjuntar archivos. En cualquier otro caso, mostramos el pdf y el frame para pagar/presentar.
	            	if (adjuntos_modadj.getArrayList().size() == 0 || estadoLiquidBD != Constantes.ESTADO_PENDIENTE_FIRMA){
	            		urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO);  
	            	}else{	            		
	            		// Obtengo todas las extensiones sin repetir que se pueden adjuntar a dicho modelo-version-concepto
	            		String extensionesAdjuntos = dameExtensionesAdjuntos(adjuntos_modadj.getArrayList()); 
	            		// Obtengo la url a direccionar a dicho modelo-version-concepto (Adjunto 762)
	            		String urlAdjunt = dameUrlAdjunto(adjuntos_modadj.getArrayList());
	            		//Recojo el mapa de casillas del documento y lo borro de session
            			HashMap casillaInfoMap = (HashMap)request.getSession().getAttribute("mapaCasillasDocumentoOriginal");
            			request.getSession().removeAttribute("mapaCasillasDocumentoOriginal");
	            		//Este m�todo (getValorCasillaHash) lo pongo en el padre BaseServlet para poder cogerlo en este servlet
            			//Recupero el numeroDocAdjunto de la casilla del modelo
            			String codfiche = getValorCasillaHash(request, userProfile, casillaInfoMap, idLiquidacion.substring(0, 3), idLiquidacion.substring(3, 4), Constantes.CASILLA_NUMERO_DOCUMENTO_ADJUNTO, "1");
            			       
	            		if (null == extensionesAdjuntos || (null != extensionesAdjuntos && "".equals(extensionesAdjuntos))){
	            			throw new Exception ("La lista de extensiones permitidas es nula");
	            		}
	            		request.getSession().setAttribute("simplificada", "true");
	            		request.getSession().setAttribute("adjuntos_modadj", adjuntos_modadj);            		
	            		request.getSession().setAttribute("doc_origen_adj", consultaLiquidacionInfo.getIdLiquidacion());
	            		request.setAttribute("extensionesAdjuntos",extensionesAdjuntos);            		
	            		request.getSession().removeAttribute("documento_adjunto");
	            		
	            		// Si el contrato en de tipo funcionario R40 y el usuario es funcionario y tiene adjuntos
	            		if (userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO_R40
	            				&& userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
	            			// Colocamos en sesi�n
	            			request.getSession().setAttribute("idFirmaOrigenAdjunto", idTransaccionFirma);
	            			urlDst = continuarSimplificadaConAdjuntar (request, userProfile);
	            		}else{
	            			try{
		            			//Si existe url a redireccionar y nos envian codigo de fichero -> ADJUNTOS FASE 2
			            		if (urlAdjunt != null && !"".equals(urlAdjunt) && codfiche != null && !"".equals(codfiche)){
			            			//Hay que redireccionar a la pantalla de firma con la informaci�n del adjunto.		            			
			            			//Recupero toda la informaci�n de datos asociada al BLOB adjuntado en proceso de confecci�n del modelo 762.
			            			FicheroAdjuntoInfo ficheroAdjuntoInfo = new FicheroAdjuntoInfo();
			            			ficheroAdjuntoInfo = getLiquidacionBLJBean(request).getInformacionFicheroAdjuntoAlmacenada(codfiche, idLiquidacion.substring(0, 3));
			            			
			            			if (ficheroAdjuntoInfo != null){
			            				//ficheroAdjuntoInfo.setCodFicheroAdjunto("FIC1000000001");
			            				request.setAttribute("codfiche", codfiche);	
			            				request.setAttribute("idDocumentoFirmar", idDocumentoFirmar);
				                        //Preparamos los datos a mostrar del documento.
				                        ficheroAdjuntoInfo.setIdLiquidacionOriginal(idDocumentoFirmar);
				                        ficheroAdjuntoInfo.setNifEntidad(liquidacion.getNifSujeto());
	                                    ficheroAdjuntoInfo.setNomEntidad(liquidacion.getApellido1Sujeto());
				                        ArrayList arrayAdjuntos = adjuntos_modadj.getArrayList();
				                        Iterator it;
				                        if(arrayAdjuntos != null){
				                        	it = arrayAdjuntos.iterator();
				                        	while (it.hasNext()){
				                        		DocumentoAdjuntoInfo info = (DocumentoAdjuntoInfo)it.next();
				                        		if (info != null && "FIC".equals(info.getCodmodel())){
				                        			ficheroAdjuntoInfo.setTipoDocumentoFichero(info.getDescripcion());
				                        		}
				                        	}
				                        }
				                        request.getSession().setAttribute("ficheroAdjuntoInfo", ficheroAdjuntoInfo);
				                        //Id transaccion para poder recuperar despues de firmar el adjunto el PDF original
				                        request.getSession().setAttribute("idFirmaOrigenAdjunto", idTransaccionFirma);
				                        //Se redirecciona a la url que esta almacenada en el campo de sn_modadj para firmarlo y adjuntarlo
				                        urlDst = urlAdjunt;
			            			}else{
			            				//No hay informaci�n del fichero adjunto 
			            				//No permitimos la importaci�n sin fichero.
				                        logger.error("No hay informaci�n del fichero adjunto con codfiche: " + codfiche);
				                        
			            				//HAY QUE BORRAR EL DOCUMENTO
				                        getPresentacionBLJBean(request).deleteWebLiquidacionAndChildList(userProfile, idLiquidacion);
				                        
				                        request.setAttribute("resultInfo",new ResultInfo(true,"","No existen ficheros asociados al modelo que se intenta presentar. Este documento exige un fichero adjunto para su presentaci�n telem�tica. Por favor, contacte con el servicio de atenci�n al cliente a trav�s del apartado, Contacto, que encontrar� en el men� superior derecha."));
			            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
			            			}
				            			
			            		}else{
			            			urlDst = getUrl(Constantes.MENSAJE_ADJUNTAR_ARCHIVOS);
			            		}
			            			
	            			}catch(Exception e){
	            				 logger.error("Error grave al intentar adjuntar un fichero: " + codfiche);
	            				//HAY QUE BORRAR EL DOCUMENTO
		                        getPresentacionBLJBean(request).deleteWebLiquidacionAndChildList(userProfile, idLiquidacion);
		                        request.setAttribute("resultInfo",new ResultInfo(true,"","Se ha producido un error, este documento exige un fichero adjunto para su presentaci�n telem�tica."));
	            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	            			}
	            		}
	            	}
               	}else{
               		logger.debug("Es usuario es SIN Certificado, no puede adjuntar documentaci�n. Redirigimos a la p�gina de inicio simplificada.");                	
               		urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO); 
               	}
            }    
        } catch (Exception e) { 
            logger.error("Error en el inicio de presentacion Simplificada: " + e);
            throw new ApplicationError(new ResultInfo(true, "PDF005", 
                getSessionJBean(request).getLiteral("PDF005")), e);
        }
        return urlDst;
    }
    
    /**
     * Converts the input inverted array of bytes to a long representation.
     * Conversion is done inclusive of both the limits specified.
     * 
     * @param bytes the byte array to be converted.
     * 
     * @param start the index to start with.
     * 
     * @param end the end index.
     * 
     * @return the long value.
     */
    protected long getLong(byte[] bytes, int start, int end)
    {
        long ret = 0;
        long mask = 0;
        
        if(start < 0 || end >= bytes.length)
        {
            return ret;
        }
        
        for (int i = start, j = 0; i <= end; i++, j++)
        {
            ret |= (bytes[i] & 0xFF) << (8 * j); // mask and shift left
            mask = (mask << 8) | 0xFF; // generate the final mask
        }
        
        return ret & mask;
    }
    
    
    /**
     * Firmar fichero adjunto previamente confeccionado en web.
     * 
     * @param request the request
     * @param userProfile the user profile
     * 
     * @return the string
     */
    private String firmarFicheroAdjunto(HttpServletRequest request, UserProfileInfo userProfile){
    	String sId = null;
	    String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	    String firma = null;
	    
	    try{
	        sId = request.getParameter("id");
	        FicheroAdjuntoInfo ficheroAdjuntoInfo = (FicheroAdjuntoInfo)request.getSession().getAttribute("ficheroAdjuntoInfo");
	        ConsultaLiquidacionInfo liquidacion = (ConsultaLiquidacionInfo)request.getSession().getAttribute("liquidacionInfo");
	        LiquidacionInfo liquidacionInfo = null;
	        String navegador= request.getParameter("infoNavegador")!=null? request.getParameter("infoNavegador"):"";
	        
	        if (sId == null) {
	            throw new ApplicationError(new ResultInfo(true, "PDF003", getSession(request).getLiteral("PDF003")), new Exception("No estaba el ID de firma en la petici�n."));
		    } else {
		    	if("-1".equals(sId)){
	        		logger.error("***ERROR EN EL PROCESO DE FIRMA*** El parametro identificador de firma recibido es: "+sId+" ,no se invocara al EJB de firma.");
					throw new ApplicationError(new ResultInfo(true, "PDF003", getSession(request).getLiteral("PDF003")), new Exception("Parametro identificador de firma incorrecto = -1"));	        				
	        	}else{
	        		try {
			        	if (((HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS)).get(sId) == null) {
			            	throw new ApplicationError(new ResultInfo(true, "PDF004",getSession(request).getLiteral("PDF004")),new Exception("No estaba el ID en la lista de IDs "));
	                	}
	            	} catch (Exception e) {
	                	logger.error("Error chequeando si el ID est� en la lista de IDs ");
			            throw new ApplicationError(new ResultInfo(true, "PDF004", getSession(request).getLiteral("PDF004")), e);
	            	}
	            	try {
                        ClienteFirma clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA));
                        //Recupero el objeto certificado de Session 
                        CertificadoDigital cdAutenticado = (CertificadoDigital) request.getSession().getAttribute("__Certificado");
                        firma = request.getParameter("firma");
                        //Obtenfo la informacion del Certificado utilizado en la firma
                        
                        CertificadoDigital cdFirmante = null;
                        String certificadoFirmante = "";
                        byte[] datosFirmados = clienteFirma.getDocumento(Double.parseDouble(sId));
                        Vector<InformacionFirmante> firmantes = clienteFirma.verificarFirmaYExtraerFirmantes(Base64.decode(firma),datosFirmados);
                        if(!firmantes.isEmpty()){
                            InformacionFirmante firmante = firmantes.get(0);
                            cdFirmante = firmante.getCertificadoDigital();
                            certificadoFirmante = firmante.getCertificado();
                        }
                        
                        logger.debug("cdAutenticado:"+cdAutenticado);
                        logger.debug("cdFirmante:"+cdFirmante);
                        if(cdAutenticado.equals(cdFirmante) || ControladorSimulador.esSimuladorActivoUsuario()){
                            //Se realiza el comit de la firma.
                            firma = request.getParameter("firma");
                            clienteFirma.generarFirma(Double.parseDouble(sId), firma, certificadoFirmante, null, null);
                        }else {
                            throw new ApplicationError(new ResultInfo(true, "PDF011", getSession(request).getLiteral("PDF011")), 
                                    new Exception("El certificado utilizado en la firma no coincide con el utilizado en la autenticacion de la aplicaci�n."));
                        }
                        
                        //Como todo ha ido bien, y ya est� firmado vamos a incluirlo en BD asociado al documento 762 que estamos incorporando.
                        liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, liquidacion.getIdLiquidacion());
                        if(liquidacionInfo != null){
                        	DocumentoAdjuntoInfo docAdjDefinicion = null;
                        	DocumentoAdjuntoInfoSet adjuntos_modadj = getLiquidacionBLJBean(request).getTiposDocumentosAdjuntos(liquidacionInfo.getIdLiquidacion(), liquidacionInfo.getIdLiquidacion().substring(0,3), liquidacionInfo.getIdLiquidacion().substring(3,4), liquidacionInfo.getConcepto());
                        	if(adjuntos_modadj != null){
                        		ArrayList<DocumentoAdjuntoInfo> arrayDocumentosAdjuntoDefinidos = adjuntos_modadj.getArrayList();
                        		if (arrayDocumentosAdjuntoDefinidos != null && arrayDocumentosAdjuntoDefinidos.size() == 1){
                        			docAdjDefinicion = arrayDocumentosAdjuntoDefinidos.get(0);
                        		}else{
                        			logger.error("Hay m�s de un documento adjunto definido en la tabla de adjuntos para el modelo: "+ liquidacion.getIdLiquidacion());
    	                        	request.setAttribute("resultInfo",new ResultInfo(true,"","Se ha producido un error al firmar el documento adjunto. Pongase en contacto con el servicio de atenci�n al usuario (CEIS) de la CHAP. "));
    	            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                        		}
                        	}else{
                        		logger.error("Error al intentar recuperar el documento a firmar: "+ liquidacion.getIdLiquidacion());
	                        	request.setAttribute("resultInfo",new ResultInfo(true,"","No ha sido posible recuperar el documento original. "+ liquidacion.getIdLiquidacion()));
	            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                        	}
                        	LiquidacionPlusInfo adjuntoInfo = new LiquidacionPlusInfo();
	                    	LiquidacionInfo liquidacionInfoAdjunto = new LiquidacionInfo();
	    	           		liquidacionInfoAdjunto.setIdLiquidacion(ficheroAdjuntoInfo.getCodFicheroAdjunto());
	    	           		docAdjDefinicion.setIdDocumentoAdjunto(ficheroAdjuntoInfo.getCodFicheroAdjunto());
	    	           		liquidacionInfoAdjunto.setTipoDocumentoAdjunto(ficheroAdjuntoInfo.getTipFiche());
	    	           		liquidacionInfoAdjunto.setIdEstadoActual(Constantes.ESTADO_DOCUMENTO_ADJUNTO);
	    	           		liquidacionInfoAdjunto.setIdDocumentoFirma(Double.parseDouble(sId));
	    	           		liquidacionInfoAdjunto.setApellido1Sujeto(liquidacionInfo.getApellido1Sujeto());
	    	           		liquidacionInfoAdjunto.setNifSujeto(liquidacionInfo.getNifSujeto());
	    	           		//Distingo el concepto GENERICO en el 762
	    	           		//A partir de ahora se va a grabar con el concepto que traiga de la tabla SN_MODADJ el campo CONCE_CODCONCE_ADJ
	    	           		liquidacionInfoAdjunto.setConcepto(docAdjDefinicion.getConcepto());
	    	           		liquidacionInfoAdjunto.setAnagramaFiscalSujeto(liquidacionInfo.getAnagramaFiscalSujeto());
	    	           		liquidacionInfoAdjunto.setIdModelo(getLiquidacionBLJBean(request).getIdModelo(ficheroAdjuntoInfo.getCodFicheroAdjunto().substring(0, 3), ficheroAdjuntoInfo.getCodFicheroAdjunto().substring(3, 4)));
	    	           		liquidacionInfoAdjunto.setDocumentoOrigenAdjunto(liquidacionInfo.getIdLiquidacion());
	    	           		liquidacionInfoAdjunto.setIdUsuario(liquidacionInfo.getIdUsuario());
	    	           		liquidacionInfoAdjunto.setFechaDevengo(liquidacionInfo.getFechaDevengo());
	    	           		liquidacionInfoAdjunto.setFechaDevengoFin(liquidacionInfo.getFechaDevengoFin());
	    	           		liquidacionInfoAdjunto.setCodTerriDoc(liquidacionInfo.getCodTerriDoc());
	    	           		liquidacionInfoAdjunto.setMotivoLiquidacion("Fichero incorporado en proceso de confeccion al Modelo " + liquidacionInfo.getIdLiquidacion().substring(0, 3));
	    	           		docAdjDefinicion.setComentario(liquidacionInfoAdjunto.getMotivoLiquidacion());
	    	           		if(Utilidades.estaVacia(ficheroAdjuntoInfo.getNomFicTxt())){
	    	           			liquidacionInfoAdjunto.setReferencia(ficheroAdjuntoInfo.getNomFicOri());
	    	           		}else{
	    	           			liquidacionInfoAdjunto.setReferencia(ficheroAdjuntoInfo.getNomFicOri() +"-"+ficheroAdjuntoInfo.getNomFicTxt());
	    	           		}
	    	           		liquidacionInfoAdjunto.setEsAdjunto(true);
	    	           		liquidacionInfoAdjunto.setDocumentoOrigenAdjunto(liquidacionInfo.getIdLiquidacion());
	    	           		// Actualizo el n�mero de documentos adjuntos del documento origen
	    	           		liquidacionInfo.setNumeroAdjuntos((liquidacionInfo.getNumeroAdjuntos())+1);
	    	           		adjuntoInfo.setLiquidacionInfo(liquidacionInfoAdjunto);
	    	           		//Completo las casillas del adjunto;
	    	           		adjuntoInfo.setCasillaInfoSetObject(docAdjDefinicion.getCasillasAdjunto());
	    	           		

	    	           		// inserci�n en sn_webliq
	    	           		LiquidacionInfo liquidResult = getLiquidacionBLJBean(request).insertAdjuntoLiquidacion(userProfile, adjuntoInfo, liquidacionInfo);
	    	           		
	    	           		if(liquidResult != null 
	    	           				&& liquidResult.getResultInfo() != null && !liquidResult.getResultInfo().getError()){
	    	           			//El documento ya ha sido firmado ==> lo elimino de sesi�n
	    	                 	((HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS)).remove(sId);
	    	                 	String idFirmaOrigenAdjunto = (String)request.getSession().getAttribute("idFirmaOrigenAdjunto");
	    	                 	if(idFirmaOrigenAdjunto != null && !"".equals(idFirmaOrigenAdjunto)){
	    	                 		request.setAttribute("idFirma", idFirmaOrigenAdjunto);
	    	                 		request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, liquidacion.getIdLiquidacion());
	    	                 		//Tras grabar tenemos que irnos a la presentaci�n del documento original.
	    	                 		//Borramos remesas.
	    	                    	request.getSession().removeAttribute("ficheroByte");
	    	                    	request.getSession().removeAttribute("ficheroAdjuntoInfo");
	    	                    	
			    	           		urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO);
	    	                 	}else{
	    	                 		logger.error("Error al intentar recuperar el idFirma del documento: "+ liquidacion.getIdLiquidacion());
		                        	request.setAttribute("resultInfo",new ResultInfo(true,"","No ha sido posible recuperar el documento original. "+ liquidacion.getIdLiquidacion()));
		            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	    	                 	}
		    	           		
	    	           		}else{
	    	           			if (liquidResult != null 
		    	           				&& liquidResult.getResultInfo() != null && liquidResult.getResultInfo().getError()
		    	           				&& "ERR405".equals(liquidResult.getResultInfo().getSubCode())){
	    	           				logger.error("Error al intentar insertar el documento adjuntado con id: "+ ficheroAdjuntoInfo.getCodFicheroAdjunto());
		                        	request.setAttribute("resultInfo",new ResultInfo(true,"","Se ha producido en error al intentar incorporar el documento. El documento " +ficheroAdjuntoInfo.getCodFicheroAdjunto() + " ya existe en nuestro sistema. Para terminar el tr�mite del documento, por favor, acceda a la Plataforma de pago con el perfil utilizado en la incorporaci�n del documento.") );
	    	           			}else{
	    	           				logger.error("Error al intentar insertar el documento adjuntado con id: "+ ficheroAdjuntoInfo.getCodFicheroAdjunto());
		                        	request.setAttribute("resultInfo",new ResultInfo(true,"","Se ha producido un error al intentar incorporar el documento " +ficheroAdjuntoInfo.getCodFicheroAdjunto() + " al sistema.") );
	    	           			}
    	           				//Como no ha sido posible la inserccion del adjunto, tenemos que borrar el documento 762 original
	    	           			if (liquidacionInfo != null && liquidacionInfo.getIdLiquidacion() != null){
	    	           				getPresentacionBLJBean(request).deleteWebLiquidacionAndChildList(userProfile, liquidacionInfo.getIdLiquidacion());
	    	           			}
	            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	    	           		}
                        }else{
                        	logger.error("Error al intentar recuperar el documento padre del documento adjuntado con id: "+ ficheroAdjuntoInfo.getCodFicheroAdjunto());
                        	request.setAttribute("resultInfo",new ResultInfo(true,"","Error al intentar recuperar el documento adjuntado " +ficheroAdjuntoInfo.getCodFicheroAdjunto()));
            				urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                        }	
    	           		
    	           		
	            	}catch (FirmaException de){
            			StringBuffer sError = new StringBuffer("Peticion de Firma de documentos ids: ");
                        sError.append (ficheroAdjuntoInfo.getCodFicheroAdjunto());
                        sError.append (" por el usuario con ID, " + userProfile.getIdUsuario());
                      	//Como no ha sido posible la inserccion del adjunto, tenemos que borrar el documento 762 original
	           			if (liquidacionInfo != null && liquidacionInfo.getIdLiquidacion() != null){
	           				getPresentacionBLJBean(request).deleteWebLiquidacionAndChildList(userProfile, liquidacionInfo.getIdLiquidacion());
	           			}	
                        logger.error(getSession(request).getLiteral("PDF007")+sError.toString()+". Informacion del navegador:"+navegador, de);
            			throw new ApplicationError(new ResultInfo(true, "PDF007", getSession(request).getLiteral("PDF007")),de);		            			
	            	} catch (Exception e) {
            			StringBuffer sError = new StringBuffer("Peticion de Firma de documentos ids: ");
            			sError.append (ficheroAdjuntoInfo.getCodFicheroAdjunto());
                        sError.append (" por el usuario con ID, " + userProfile.getIdUsuario());

                        logger.error(getSession(request).getLiteral("PDF007")+sError.toString()+". Informacion del navegador:"+navegador, e);
	                	throw new ApplicationError(new ResultInfo(true, "PDF007", getSession(request).getLiteral("PDF007")), e);
	            	}
	        	}
		    }
	    }catch(Exception e){
	    	logger.error("Error al intentar recuperar el documento padre del documento adjuntado"+ e.getMessage());
        	request.setAttribute("resultInfo",new ResultInfo(true,"","Error al intentar recuperar el documento adjuntado."));
			urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	    }    
	    
	    return urlDst;
    }
    

    private String inicioPresentacionFuncionario(HttpServletRequest request, UserProfileInfo userProfile){
        //String idLiquidacion = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
         String idDocumentoFirmar = (String)request.getAttribute("idAutoliquidacion");
        if ((request.getParameter("esCif")== null && request.getParameter("errorCenso")==null && request.getParameter("esCCC")==null) || 
            (!request.getParameter("esCif").equals("true") && !request.getParameter("errorCenso").equals("true") && !request.getParameter("esCCC").equals("false"))){
            if (null != userProfile){           
                request.getSession().removeAttribute("userProfile");
                userProfile.setAutorizacion(new AutorizacionInfo());
            } 
        }
        if (idDocumentoFirmar == null){
            idDocumentoFirmar = request.getParameter("idAutoliquidacion");
        }
        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
    
        ConsultaLiquidacionInfo consultaLiquidacionInfo =  new ConsultaLiquidacionInfo();
        ConsultaLiquidacionInfo consultaLiqIdemAnteriorInfo =  new ConsultaLiquidacionInfo();

        try{            
            String idLiquidacion = idDocumentoFirmar;
            //Controlamos si un modelo es 762, enviamos a error porque ese modelo no se puede presentar por telematica Empleado Publico
            long idModelo = getLiquidacionBLJBean(request).getIdModelo(idLiquidacion.substring(0, 3),idLiquidacion.substring(3, 4));
            if (idLiquidacion != null && idModelo == Constantes.ID_MODELO_762){
            	logger.error("El modelo 762 que intenta importar a la Plataforma no est� habilitado para esta autorizaci�n: Modelo: " + idLiquidacion);
                request.setAttribute("resultInfo",new ResultInfo(true,"","La presentaci�n telem�tica del modelo 762 no est� disponible para autorizaciones de tipo 'Empleado P�blico'."));
                return urlDst;
            }
            //Obtenemos la liquidacion para poder obtener su modelo correspondiente.
            LiquidacionInfo liquidacion = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
            //Obtener las oficinas de presentacion y el combo con las entidades financieras.
            cargarDatosComboEntidadesFinancieras(request, userProfile);
            
            ResultInfo resultInfo = getCombosOficinaPresentacion(request, userProfile, liquidacion);
            if (resultInfo.getError())
                throw new Exception();
            //GestionPermisos: Me trae la informaci�n relativa a ese documento
            consultaLiquidacionInfo = getLiquidacionBLJBean(request).getConsultaLiquidacion(userProfile,liquidacion);
            //consultaLiquidacionInfo.setLiquidacionInfo909(liquidacion.getLiquidacionInfo909());
        
            //Buscariamos un documento con nif y concepto igual al de la liquidacion actual con la misma autorizaci�n
            //y si existe me traigo los datos de pago. 
            consultaLiqIdemAnteriorInfo = getLiquidacionBLJBean(request).getConsultaLiqIdemAnterior(userProfile,liquidacion);
            
            if(consultaLiqIdemAnteriorInfo != null && consultaLiqIdemAnteriorInfo.getResultInfo() != null
                    && !consultaLiqIdemAnteriorInfo.getResultInfo().getError()){
                //Existe documento
                //Almaceno variable para que en la jsp salga el bot�n.
                request.setAttribute("muestraBotonCopiaDatosPago", "true");
                if(consultaLiqIdemAnteriorInfo.getCodEntidad() != 0 && consultaLiqIdemAnteriorInfo.getSucursal() != null){
                    consultaLiqIdemAnteriorInfo.setIban(getContratoBLJBean(request).calcularIban(userProfile,String.valueOf(consultaLiqIdemAnteriorInfo.getCodEntidad()), 
                            consultaLiqIdemAnteriorInfo.getSucursal(), consultaLiqIdemAnteriorInfo.getDigitoControl(), consultaLiqIdemAnteriorInfo.getCuenta()));
                }                
                
            }else if(consultaLiqIdemAnteriorInfo != null && consultaLiqIdemAnteriorInfo.getResultInfo() != null
                    && consultaLiqIdemAnteriorInfo.getResultInfo().getError()){
                //No existe documento
                //Almaceno variable para que en la jsp no salga el bot�n.
                request.setAttribute("muestraBotonCopiaDatosPago", "false");
            }else{
                request.setAttribute("muestraBotonCopiaDatosPago", "false");
            }
            
            if(consultaLiquidacionInfo.getIdLiquidacion() != null) {
            	consultaLiquidacionInfo = getLiquidacionBLJBean(request).consultaEsDiligenciable(userProfile,consultaLiquidacionInfo);
            }
            
			
			// Si estamos en pago diferido eliminamos las entidades financieras que no permitan el pago diferido
			ArrayList arrayEntidades = null;
			EntidadFinancieraInfoSet entidadInfoSet = null;
			if ((userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO)|| (String) request.getSession().getAttribute("pagoSimplificado") != null) {
				entidadInfoSet = (EntidadFinancieraInfoSet) request.getSession().getAttribute("infoSetEntidadFinanciera");
			} else {
				entidadInfoSet = (EntidadFinancieraInfoSet) request.getAttribute("infoSetEntidadFinanciera");
			}
			if (null != entidadInfoSet) {
				arrayEntidades = (ArrayList) entidadInfoSet.getArrayList();
				if (null == arrayEntidades || (null != arrayEntidades && arrayEntidades.size() < 1)) {
					request.setAttribute("resultInfo", new ResultInfo(true, null,
							"En estos momentos ninguna de las entidades financieras adscritas a la Plataforma Telem�tica de Pago admite el pago con NRC diferido. Disculpe las molestias."));
					throw new Exception("Estamos en pago diferido y no existen endidades financieras que admitan pago diferido");
				} else {
					entidadInfoSet.setArrayList(arrayEntidades);
					// almacenamos el nuevo array
					if ((userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO) || (String) request.getSession().getAttribute("pagoSimplificado") != null) {
						request.getSession().setAttribute("infoSetEntidadFinancieraDiferido", entidadInfoSet);
					} else {
						request.setAttribute("infoSetEntidadFinancieraDiferido", entidadInfoSet);
					}
				}
			} else {
				request.setAttribute("resultInfo", new ResultInfo(true, null,
						"En estos momentos ninguna de las entidades financieras adscritas a la Plataforma Telem�tica de Pago admite el pago con NRC diferido. Disculpe las molestias."));
				throw new Exception("Estamos en pago diferido y el combo de entidades financieras es nulo");
			}
            
            request.getSession().setAttribute("userProfile", userProfile); 
            request.getSession().setAttribute("liquidacionInfo", consultaLiquidacionInfo);
            request.getSession().setAttribute("liquidacionInfoCopiaAnterior", consultaLiqIdemAnteriorInfo);
            request.getSession().setAttribute("simplificada",(String) request.getSession().getAttribute("simplificado"));
            if(getLiquidacionBLJBean(request).compruebaAutorizacionPagoTarjeta(idLiquidacion)
            		&& Utilidades.estaVacia(request.getParameter("codigoIban")) ){
            	request.setAttribute("pagoTarjeta","pagoTarjeta");
            }
            
            if(consultaLiquidacionInfo.getIdDiligencia().intValue()!=0){
                //Mostramos informacion acerca del estado de la diligencia de presentacion
                String aux = "00"+consultaLiquidacionInfo.getIdDiligencia();
                if (aux.length()>3){
                    //Quito el primer cero para tener 3digitos
                    aux = aux.substring(1);
                }
                request.setAttribute("mensajeEmpleadoPublico",getSessionJBean(request).getLiteral("DIL"+aux));
            }
                        
            if(consultaLiquidacionInfo.getIdDiligencia().intValue()!=0){
                //Mostramos informacion acerca del estado de la diligencia de presentacion
                String aux = "00"+consultaLiquidacionInfo.getIdDiligencia();
                if (aux.length()>3){
                    //Quito el primer cero para tener 3digitos
                    aux = aux.substring(1);
                }
                request.setAttribute("mensajeEmpleadoPublico",getSessionJBean(request).getLiteral("DIL"+aux));
            }
                        
            String pagoTarjeta = (String)request.getAttribute("pagoTarjeta");
            logger.debug("inicioPresentacionFuncionario: PagoTarjeta: " +  pagoTarjeta);
            urlDst = getUrl(Constantes.EXPEDIR_AUTORIZACION);          
        
        } catch (Exception e) {
            logger.error("Error inicioPresentacionFuncionario. ", e);
            throw new ApplicationError(new ResultInfo(true, "PDF005", 
                getSessionJBean(request).getLiteral("PDF005")), e);
        }
        return urlDst;
    } 

    /**
     * Metodo que genera la autorizacion del funcionario junto con el pdf y muestra la pantalla de aceptacion de cargo
     * segun sea el pago con TPV PinPad o contra CCC.
     * @author VAS000 23/03/2007
     * @param request
     * @param response
     * @param userProfile
     * @return urlDst
     * */
    private String datosFuncionario(HttpServletRequest request, HttpServletResponse response,
			UserProfileInfo userProfile) throws Exception {

		ValidacionRequest validaRequest = new ValidacionRequest(request);
		boolean error = false;
		boolean NoValidaNIFPagador = false;
		String nombrePagador = request.getParameter("nombrePagador");
		String nombrePagador2 = request.getParameter("nombrePagador2");
		String esTarjetaFuncionario = "";
		String esPagoNRC = "";
		String esPagoJustificanteRecibido = "";
		ConsultaLiquidacionInfo liquidacion = (ConsultaLiquidacionInfo) request.getSession().getAttribute("liquidacionInfo");
		ConsultaLiquidacionInfo liquidacionAnterior = getLiquidacionBLJBean(request).getUltimaEvolucion(userProfile, liquidacion.getIdLiquidacion());
		esTarjetaFuncionario = request.getParameter("pagoTarjeta");
		esPagoNRC = (String) request.getParameter("pagoNRC");
		esPagoJustificanteRecibido = (String) request.getParameter("justificanteRecibido");
		String url = "";
		if (esTarjetaFuncionario == null) {
			esTarjetaFuncionario = (String) request.getAttribute("pagoTarjeta");
		}
		logger.debug("datosFuncionario: Hemos pulsado pago con tarjeta? " + esTarjetaFuncionario);
		boolean liquidacionDeNoPago = false;

		String pagoCuentaMancomunada = request.getParameter("checkmancomunada");
		boolean esCuentaMancomunada = false;

		try {
			if (liquidacion == null || Utilidades.estaVacia(liquidacion.getIdLiquidacion())) {
				throw new Exception("No hemos recuperado de sesi�n la liquidaci�n correctamente.");
			}

			// Cuenta Mancomunada
			if (!Utilidades.estaVacia(pagoCuentaMancomunada)) {
				esCuentaMancomunada = true;
				// Paso a mayusculas el nombre del pagador, por si acaso
				if (!Utilidades.estaVacia(nombrePagador2)) {
					nombrePagador2 = nombrePagador2.toUpperCase();
				}
			}

			// Paso a mayusculas el nombre del pagador, por si acaso
			if (!Utilidades.estaVacia(nombrePagador)) {
				nombrePagador = nombrePagador.toUpperCase();
			}

			if (Utiles.validaCIF(validaRequest.getParameter("nifPagador"))) {
				request.setAttribute("idAutoliquidacion", liquidacion.getIdLiquidacion());
				request.setAttribute("esCif", "true");
				error = true;
			}

			CensoRespInfo censoRespInfo = null;
			CensoRespInfo censoRespInfoCotitular = null;

			String modeloConsulta = liquidacion.getIdLiquidacion().substring(0, 3);
			String versionConsulta = liquidacion.getIdLiquidacion().substring(3, 4);
			logger.debug("modeloConsulta: " + modeloConsulta);
			logger.debug("versionConsulta: " + versionConsulta);
			liquidacionDeNoPago = getLiquidacionBLJBean(request).compruebaLiquidacionDePago(modeloConsulta,
					versionConsulta);

			ModeloAmpliadoInfoSet modelos = getLiquidacionBLJBean(request).getModeloAmpliados(userProfile,
					modeloConsulta, versionConsulta);

			boolean modeloTipoPago2 = false;
			if (modelos != null && modelos.getResultInfo() != null && modelos.getResultInfo().getError()) {
				logger.error("Error al obtener la informaci�n del modelo: " + modeloConsulta + " " + versionConsulta);
				throw new Exception(
						"Error al obtener la informaci�n del modelo: " + modeloConsulta + " " + versionConsulta);
			} else if (modelos != null && modelos.getArrayList() != null && modelos.getArrayList().size() == 1) {
				ModeloAmpliadoInfo modelo = (ModeloAmpliadoInfo) modelos.getArrayList().get(0);
				if ("2".equals(modelo.getLiteralPago())) {
					modeloTipoPago2 = true;
				}
			}

			logger.debug("LiquidacionDeNoPago: " + liquidacionDeNoPago);
			logger.debug("esTarjetaFuncionario: " + esTarjetaFuncionario);
			if (!liquidacionDeNoPago && esTarjetaFuncionario != null && esTarjetaFuncionario.equals("pagoTarjeta")) {
				// Introducimos una excepcion. Si el modelo es de tipo pago 2 y es pago con
				// tarjeta, validamos el pagador.
				if (!modeloTipoPago2)
					NoValidaNIFPagador = true;
			}
			logger.debug("NoValidaNIFPagador: " + NoValidaNIFPagador + " , si es false, VALIDA PAGADOR");
			// Si el documento es de pago y es con tarjeta, no hay verificaci�n de NIF del
			// pagador. puede pagar cualquiera
			if (!error && !NoValidaNIFPagador) {
				censoRespInfo = getContratoBLJBean(request).verificaNifCif(userProfile,
						validaRequest.getParameter("nifPagador"), nombrePagador);
				if ((nombrePagador != null) && (censoRespInfo.getResultInfo().getError())) {
					request.setAttribute("idAutoliquidacion",
							((ConsultaLiquidacionInfo) request.getSession().getAttribute("liquidacionInfo"))
									.getIdLiquidacion());
					request.setAttribute("errorCenso", "true");
					error = true;
				}
				// Valido los datos del pagador2/Representante2
				if (esCuentaMancomunada) {
					censoRespInfoCotitular = getContratoBLJBean(request).verificaNifCif(userProfile,
							validaRequest.getParameter("nifPagador2"), nombrePagador2);
					if ((nombrePagador2 != null) && (censoRespInfoCotitular.getResultInfo().getError())) {
						request.setAttribute("idAutoliquidacion",
								((ConsultaLiquidacionInfo) request.getSession().getAttribute("liquidacionInfo"))
										.getIdLiquidacion());
						request.setAttribute("errorCensoCotitular", "true");
						error = true;
					}
				}
			} else {
				if (NoValidaNIFPagador) {
					// Relleno el objeto censo para que no haya problemas con los datos del
					// formulario
					censoRespInfo = new CensoRespInfo();
					censoRespInfo.setNif(validaRequest.getParameter("nifPagador"));
					censoRespInfo.setSujeto(nombrePagador);
				}
			}

			// AQUI TENDREMOS QUE DISTINGUIR ENTRE SI ES PAGO CON TARJETA O ES PAGO CONTRA
			// CUENTA BANCARIA
			// Si es tarjeta, -> iremos a la generacion del AU X, aunque no se sabe como lo
			// haremos
			// Si es cuenta bancaria, -> igual que antes
			if (esTarjetaFuncionario != null && esTarjetaFuncionario.equals("pagoTarjeta")) {
				// Forma de Pago contra TPV tarjeta PinPad
				// DEBEMOS DE TOMAR LA DECISION DE LO QUE SE VA A HACER
				// Veo si existe CCC por si antes ha expedido una autorizacion con CCC
				String entfin = userProfile.getAutorizacion().getCodEntidad();
				String sucursal = userProfile.getAutorizacion().getCodSucursal();
				String dc = userProfile.getAutorizacion().getDc();
				String cuenta = userProfile.getAutorizacion().getCuenta();
				if (error) {
					request.setAttribute("nombrePagador", nombrePagador);
					request.setAttribute("nifPagador", validaRequest.getParameter("nifPagador"));
					request.setAttribute("pagoTarjeta", esTarjetaFuncionario);

					return getUrl(Constantes.EXPEDIR_AUTORIZACION_FRAME);
				} else {
					if (userProfile.getAutorizacion().getIdAutorizacion() == null) {
						// No se ha expedido autorizacion anteriormente
						AutorizacionInfo autorizacion = new AutorizacionInfo();
						userProfile.setAutorizacion(autorizacion);
					}

					ConsultaLiquidacionInfo consultaLiquidacion = (ConsultaLiquidacionInfo) request.getSession()
							.getAttribute("liquidacionInfo");

					// Miramos si antes fue expedida una autorizacion con CCC
					if (entfin != null && sucursal != null && dc != null && cuenta != null) {
						// Como hay CCC, debemos de expedir una nueva autorizacion
						error = true;// datos modificados
						userProfile.getAutorizacion().setNifPagador(censoRespInfo.getNif());
						userProfile.getAutorizacion().setNombrePagador(censoRespInfo.getSujeto());
						userProfile.getAutorizacion()
								.setEsRepresentante(getEsRepresentante(request, userProfile, consultaLiquidacion));
					} else {
						// Validacion de tarjeta junto con autorizacion ya que quitamos lo referido a
						// CCC
						// O no se ha expedido la autorizacion anteriormente o se han cambiado datos con
						// respecto a la ultima autorizacion
						if ((userProfile.getAutorizacion().getIdAutorizacion() == null
								&& userProfile.getAutorizacion().getCodigoIbanObject() == null) ||

								((userProfile.getAutorizacion().getIdAutorizacion() != null) && ((!userProfile
										.getAutorizacion().getNombrePagador().equals(censoRespInfo.getSujeto()))
										|| (!userProfile.getAutorizacion().getNifPagador()
												.equals(censoRespInfo.getNif()))))) {
							error = true;// datos modificados
							userProfile.getAutorizacion().setNifPagador(censoRespInfo.getNif());
							userProfile.getAutorizacion().setNombrePagador(censoRespInfo.getSujeto());
							userProfile.getAutorizacion()
									.setEsRepresentante(getEsRepresentante(request, userProfile, consultaLiquidacion));
						}

					}

					request.getSession().removeAttribute("liquidacionImportada");

					request.getSession().setAttribute("userProfile", userProfile);

					// Indicador de Tarjeta
					userProfile.getAutorizacion().setTarjeta("true");
					String idLiquidacion = consultaLiquidacion.getIdLiquidacion();
					request.setAttribute("pagoTarjeta", esTarjetaFuncionario);
					// Si es de pago el m�todo devuelve false -> 046 (1) = FALSE
					if (liquidacionDeNoPago) {
						consultaLiquidacion.setPagoSinAutorizacion(false);
						request.getSession().setAttribute("liquidacionInfo", consultaLiquidacion);
						mostrarAutorizacion(request, userProfile, error);
						url = getUrl(Constantes.PRESENTACION_FUNCIONARIO_FRAME);
					} else {
						consultaLiquidacion.setPagoSinAutorizacion(true);
						request.getSession().setAttribute("order", consultaLiquidacion.getLiquidacionInfo909().getIdLiquidacion());
						request.getSession().setAttribute("esTarjeta", esTarjetaFuncionario);
						// Obtenemos el liquidacion Info correspondiente y creamos el presentacion info
						// para despues
						LiquidacionInfo liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile,
								idLiquidacion);
						liquidacionInfo.setJustificante(liquidacionInfo.getIdLiquidacion());
						PresentacionInfo presentacionInfo = new PresentacionInfo(liquidacionInfo);
						ResultInfo resultInfo = null;
						if (liquidacion.getIdEstadoActual() == Constantes.ESTADO_PENDIENTE_FIRMA) {
							liquidacion.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
							liquidacionInfo.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
							resultInfo = completarDatosEvolucion(request, userProfile,
									new PresentacionInfo(liquidacionInfo));
							resultInfo = getLiquidacionBLJBean(request).setLiquidacion(userProfile, liquidacionInfo);
							if (resultInfo.getError()) {
								return getUrl(Constantes.MOSTRAR_ERROR);
							}
						}
						// Generamos las casillas de presentacion si procede
						ModeloAmpliadoInfo modeloAmpliadoInfo = getPresentacionBLJBean(request).getModelo(userProfile,
								consultaLiquidacion.getIdModelo());
						NumeroSURWebPetInfo numeroSURWebPetInfo = getModeloAutorizacion(request, userProfile,
								consultaLiquidacion, modeloAmpliadoInfo);

						if (numeroSURWebPetInfo != null && numeroSURWebPetInfo.getResultInfo() != null
								&& numeroSURWebPetInfo.getResultInfo().getError()) {
							throw new Exception("Error al crear las casillas de presentaci�n del documento "
									+ consultaLiquidacion.getIdLiquidacion());
						}

						// PAGO CON TARJETA
						int bloqueado = 1;
						if (presentacionInfo != null && presentacionInfo.getTotalIngresar() > 0) {
							// Bloquear documento y Actualizar la evolucion del documento, insertando dos
							// estado un Intento Pago y un Pendiente Pagar/Presentar
							bloqueado = bloqueoActEvolucion(request, presentacionInfo, Constantes.GESTION_PRES_ERROR,
									"FUNCIONARIO");
							if (bloqueado != 0) {
								resultInfo = new ResultInfo(true, Constantes.GESTION_PRES_ERROR,
										getSessionJBean(request).getLiteral(Constantes.GESTION_PRES_ERROR));
								throw new ApplicationError(resultInfo, new Exception("Error al bloquear el documento "
										+ presentacionInfo.getIdLiquidacion() + " en el inicio pago con tarjeta."));
							}

							if (presentacionInfo.getTotalIngresar() != 0) {
								DecimalFormat df = new DecimalFormat("#.##");

								logger.debug("Importe a pagar (sin mascara): " + presentacionInfo.getTotalIngresar());
								String importe = df.format(presentacionInfo.getTotalIngresar() * 100);
								logger.debug("Importe a pagar (con mascara): " + importe);

								request.setAttribute("importeTotal", importe);
								request.setAttribute("idLiquidacion", presentacionInfo.getIdLiquidacion());
								// Pago con tarjeta Empleado P�blico
								url = getUrl(Constantes.PAGO_TPV_PINPAD);
							} else {
								logger.error("datosFuncionario: El importe es nulo");
								throw new Exception("El importe es nulo, por lo que no podemos pagar nada....");
							}
						}
					}
				}
				//Nueva modalidad, Pago NRC o por NRC Ficticio debido a la presentaci�n del Justfificante de Pago
				} else if ((!Utilidades.estaVacia(esPagoNRC) && esPagoNRC.equals("pagoNRC"))
					|| (!Utilidades.estaVacia(esPagoJustificanteRecibido) && esPagoJustificanteRecibido.equals("justificanteRecibido"))) {
					logger.debug("Proceso por NRC: Validamos el NRC del modo de pago NRC diferido antes de continuar");
					ConsultaLiquidacionInfo consultaLiquidacion = (ConsultaLiquidacionInfo) request.getSession().getAttribute("liquidacionInfo");
					
					if (error) {
						request.setAttribute("nombrePagador", nombrePagador);
						request.setAttribute("nifPagador", validaRequest.getParameter("nifPagador"));
						if(!Utilidades.estaVacia(esPagoJustificanteRecibido) && esPagoJustificanteRecibido.equals("justificanteRecibido")) {
							//Modalidad justificante previo
							request.getSession().setAttribute("justificantePagoAnterior", "justificanteRecibido".equals(esPagoJustificanteRecibido) ? "true" : "false");
							request.getSession().setAttribute("fechaJustificantePagoAnterior", request.getParameter("fechaPagoJustificanteRecibido")!=null ? request.getParameter("fechaPagoJustificanteRecibido") : "");
						}else {
							//Modalidad por NRC
							request.getSession().setAttribute("codEntidadFinancieraNRCAnterior", request.getParameter("codEntidadFinanciera")!=null ? request.getParameter("codEntidadFinanciera") : "");
							request.getSession().setAttribute("nrcDiferido", request.getParameter("nrcDiferido")!=null ? request.getParameter("nrcDiferido") : "");
							request.getSession().setAttribute("fechaPagoDiferido", request.getParameter("fechaPagoDiferido")!=null ? request.getParameter("fechaPagoDiferido") : "");
						}

						return getUrl(Constantes.EXPEDIR_AUTORIZACION_FRAME);
					}else {
						//Preparamos el userProfile
						String fechaPagoDiferido = request.getParameter("fechaPagoDiferido")!=null?request.getParameter("fechaPagoDiferido"):"";
						if(Utilidades.estaVacia(fechaPagoDiferido)) {
							fechaPagoDiferido = request.getParameter("fechaPagoJustificanteRecibido")!=null?request.getParameter("fechaPagoJustificanteRecibido"):"";
						}
						SimpleDateFormat sdf = new SimpleDateFormat ("dd/MM/yyyy");
						Date fechaPago = sdf.parse(fechaPagoDiferido);
						if (userProfile.getAutorizacion().getIdAutorizacion() == null) {
							// No se ha expedido autorizacion anteriormente
							AutorizacionInfo autorizacion = new AutorizacionInfo();
							userProfile.setAutorizacion(autorizacion);
							userProfile.getAutorizacion().setNifPagador(censoRespInfo.getNif());
							userProfile.getAutorizacion().setNombrePagador(censoRespInfo.getSujeto());
							userProfile.getAutorizacion().setEsRepresentante(getEsRepresentante(request, userProfile, consultaLiquidacion));
							userProfile.getAutorizacion().setFechaPago(fechaPago);
						}
					
						//Si se nos indica que se ha recibido el justificante del pago, calcularemos el nrc mediante el codigo de entidad 9999
						String nrc = "";
						if(!Utilidades.estaVacia(esPagoJustificanteRecibido) && esPagoJustificanteRecibido.equals("justificanteRecibido")) {
							userProfile.getAutorizacion().setCodEntidad(Utiles.getPropiedadesSiri().getString("codigoEntidadFicticia"));
							userProfile.getAutorizacion().setCodEntidadObject(Utiles.getPropiedadesSiri().getString("codigoEntidadFicticia"));
							nrc = calculaNrcFicticio(request, userProfile, consultaLiquidacion.getNifSujetoPasivo());
							
						}else {
							userProfile.getAutorizacion().setCodEntidad(request.getParameter("codEntidadFinanciera"));
							userProfile.getAutorizacion().setCodEntidadObject(request.getParameter("codEntidadFinanciera"));
							nrc = request.getParameter("nrcDiferido");
						}
						userProfile.getAutorizacion().setNrc(nrc);
						userProfile.getAutorizacion().setJustificantePagoRecibido(request.getParameter("justificanteRecibido")!=null);

						consultaLiquidacion.setFechaPagoNrc(fechaPago);
						consultaLiquidacion.setPagoSinAutorizacion(false);
						consultaLiquidacion.setNrc(nrc);
						request.getSession().setAttribute("liquidacionInfo", consultaLiquidacion);
						
						String idLiquidacion = consultaLiquidacion.getIdLiquidacion();
						LiquidacionInfo liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile,
								idLiquidacion);
						PresentacionInfo presentacionInfo = new PresentacionInfo(liquidacionInfo);
						
						if(esPagoNRC!=null && esPagoNRC.equals("pagoNRC")) {
							url = validaNRCDiferidoEP(request, userProfile, presentacionInfo);
						}
						
						if (url != null && !url.isEmpty()) {
		                	return url;
		                }else {
		                	mostrarAutorizacion(request, userProfile, true);
		                }
					}

			} else if ((esTarjetaFuncionario == null && esPagoNRC == null)
					|| (esTarjetaFuncionario != null && !esTarjetaFuncionario.equals("pagoTarjeta"))) {
				// Forma de pago contra CCC
				// jatoribio 09/05/2006: a�adimos validaci�n en Java del codigo de la cuenta
				// cuenta corriente
				String codigoIban = request.getParameter("codigoIban") != null ? request.getParameter("codigoIban")
						: "";
				logger.debug("codigoIban:" + codigoIban);
				boolean ibanCorrecto = false;
				if (!StringUtils.isEmpty(codigoIban) && codigoIban.length() == Constantes.LONG_IBAN_ESP) {
					String entidad = codigoIban.substring(4, 8);
					String sucursal = codigoIban.substring(8, 12);
					String dc = codigoIban.substring(12, 14);
					String numeroCuenta = codigoIban.substring(14);

					String ibanCalculado = getContratoBLJBean(request).calcularIban(userProfile, entidad, sucursal, dc,
							numeroCuenta);
					ibanCorrecto = codigoIban.equals(ibanCalculado);

				}
				if (!error && liquidacion != null && liquidacion.getTotalIngresar() > 0.0 && !ibanCorrecto) {
					// Error en el CCC
					request.setAttribute("idAutoliquidacion",
							((ConsultaLiquidacionInfo) request.getSession().getAttribute("liquidacionInfo"))
									.getIdLiquidacion());
					request.setAttribute("esCCC", "false");
					error = true;
				}

				if (error) {
					request.setAttribute("entidadFinanciera", validaRequest.getParameter("entidadFinanciera"));
					request.setAttribute("codigoIban", validaRequest.getParameter("codigoIban"));
					request.setAttribute("nombrePagador", nombrePagador);
					request.setAttribute("nifPagador", validaRequest.getParameter("nifPagador"));
					if (esCuentaMancomunada) {
						request.setAttribute("nombrePagador2", nombrePagador2);
						request.setAttribute("nifPagador2", validaRequest.getParameter("nifPagador2"));
						request.setAttribute("esCuentaMancomunada", "true");
					}
					request.setAttribute("pagoTarjeta", null);

					return getUrl(Constantes.EXPEDIR_AUTORIZACION_FRAME);
				} else {
					String entidadFinanciera = request.getParameter("entidadFinanciera");
					String ibanFormateado = null;

					if (userProfile.getAutorizacion().getIdAutorizacion() == null) {
						// No se ha expedido autorizacion anteriormente
						AutorizacionInfo autorizacion = new AutorizacionInfo();
						userProfile.setAutorizacion(autorizacion);
					}

					ConsultaLiquidacionInfo consultaLiquidacion = (ConsultaLiquidacionInfo) request.getSession()
							.getAttribute("liquidacionInfo");

					// Distingo si viene de expedir una autorizacion con tarjeta o sin tarjeta
					if (userProfile.getAutorizacion().getCodigoIban() == null && consultaLiquidacion.getTotalIngresar() != 0 ) {
						// Como esta cambiando de modo de pago hay que expedir una nueva autorizacion
						error = true;// datos modificados
						userProfile.getAutorizacion().setNifPagador(censoRespInfo.getNif());
						userProfile.getAutorizacion().setNombrePagador(censoRespInfo.getSujeto());
						if (esCuentaMancomunada) {
							userProfile.getAutorizacion().setNifPagador2(censoRespInfoCotitular.getNif());
							userProfile.getAutorizacion().setNombrePagador2(censoRespInfoCotitular.getSujeto());
							userProfile.getAutorizacion().setEsPagoMancomunado(true);
						}
						userProfile.getAutorizacion()
								.setEsRepresentante(getEsRepresentante(request, userProfile, consultaLiquidacion));
					} else {

						if (modificarAutorizacion(userProfile, entidadFinanciera, codigoIban, censoRespInfo,
								censoRespInfoCotitular)) {

							// O no se ha expedido la autorizacion anteriormente o se han cambiado datos con
							// respecto a la ultima autorizacion
							error = true;// datos modificados
							userProfile.getAutorizacion().setNifPagador(censoRespInfo.getNif());
							userProfile.getAutorizacion().setNombrePagador(censoRespInfo.getSujeto());
							userProfile.getAutorizacion()
									.setEsRepresentante(getEsRepresentante(request, userProfile, consultaLiquidacion));

							if (esCuentaMancomunada) {
								userProfile.getAutorizacion().setNifPagador2(validaRequest.getParameter("nifPagador2"));
								userProfile.getAutorizacion().setNombrePagador2(nombrePagador2);
								userProfile.getAutorizacion().setEsPagoMancomunado(true);
							} else {
								userProfile.getAutorizacion().setEsPagoMancomunado(false);
								userProfile.getAutorizacion().setNifPagador2(null);
								userProfile.getAutorizacion().setNombrePagador2(null);
							}

						}
					}
					request.getSession().removeAttribute("liquidacionImportada");

					// Datos entidad financiera
					request.setAttribute("entidadFinanciera", validaRequest.getParameter("entidadFinanciera"));
					request.setAttribute("codigoIban", validaRequest.getParameter("codigoIban"));
					request.getSession().setAttribute("nombreEntidadFinanciera",
							validaRequest.getParameter("nombreEntidadFinanciera"));

					if (null != request.getParameter("entidadFinanciera")) {

						// Guardamos los datos en el userProfile

						userProfile.getAutorizacion().setCodEntidad(entidadFinanciera);
						userProfile.getAutorizacion().setCodigoIban(codigoIban);
						userProfile.getAutorizacion().setTarjeta(null);
						userProfile.getAutorizacion().setFechaCadTarjeta(null);

						ibanFormateado = Utiles.formateaIban(codigoIban);
						if (!StringUtils.isEmpty(ibanFormateado))
							consultaLiquidacion.setIban(ibanFormateado);

					}
					request.getSession().setAttribute("userProfile", userProfile);

					userProfile.getAutorizacion().setTarjeta("false");
					mostrarAutorizacion(request, userProfile, error);
				}
			} else {
				logger.error("datosFuncionario: Debe de seleccionar una forma de pago");
				throw new Exception("Debe de seleccionar una forma de pago");
			}
			String simplificadaAdjunto = (String) request.getSession().getAttribute("simplificada");
			if (null != simplificadaAdjunto && !"".equals(simplificadaAdjunto) && "true".equals(simplificadaAdjunto)) {
				// Debe ser SIN CERTIFICADO para poder adjuntar ficheros
				if (userProfile.getIdUsuario() != 444) {
					// Adjuntar documentacion;
					// Obtenemos los tipos de modelos que puede adjuntar para el
					// modelo-version-concepto dados
					ModeloAmpliadoInfo modelo = getLiquidacionBLJBean(request).getModelo(userProfile,
							liquidacion.getIdModelo());
					DocumentoAdjuntoInfoSet adjuntos_modadj = getLiquidacionBLJBean(request).getTiposDocumentosAdjuntos(
							liquidacion.getIdLiquidacion(), modelo.getModelo(), modelo.getVersion(),
							liquidacion.getConcepto());
					// Vemos si ya tiene adjuntado alg�n documento
					DocumentoAdjuntoInfoSet adjuntos_webliq = getLiquidacionBLJBean(request)
							.getDocAdjuntosWebliq(liquidacion.getIdLiquidacion());
					// Eliminamos si hab�a alg�n adjunto webliq en sesion y colocamos el nuevo
					request.getSession().removeAttribute("adjuntos_webliq");
					if (null != adjuntos_webliq) {
						request.getSession().setAttribute("adjuntos_webliq", adjuntos_webliq);
						// Vemos si ya est�n todos correctos y lo guardo en sesi�n
						boolean estanCorrectos = getLiquidacionBLJBean(request)
								.todosDocumentosAdjuntosCorrectos(adjuntos_modadj, adjuntos_webliq);
						request.getSession().setAttribute("adjuntosCorrectos", String.valueOf(estanCorrectos));
					}
					// Comprobamos si podemos adjuntar alg�n documento a este
					// modelo_version_concepto y si el docuemnto esta pendiente de firma (estado=8).
					// En caso afirmativo mostramos la ventana para adjuntar archivos. En cualquier
					// otro caso, mostramos el pdf y el frame para pagar/presentar.
					if (adjuntos_modadj.getArrayList().size() > 0
							&& liquidacion.getIdEstadoActual() == Constantes.ESTADO_PENDIENTE_FIRMA) {
						// Obteno todas las extensiones sin repetir que se pueden adjuntar a dicho
						// modelo-version-concepto
						String extensionesAdjuntos = dameExtensionesAdjuntos(adjuntos_modadj.getArrayList());
						if (null == extensionesAdjuntos
								|| (null != extensionesAdjuntos && "".equals(extensionesAdjuntos))) {
							throw new Exception("La lista de extensiones permitidas es nula");
						}
						request.getSession().setAttribute("simplificada", "true");
						request.getSession().setAttribute("adjuntos_modadj", adjuntos_modadj);
						request.getSession().setAttribute("doc_origen_adj", liquidacion.getIdLiquidacion());
						request.setAttribute("extensionesAdjuntos", extensionesAdjuntos);
						request.getSession().removeAttribute("documento_adjunto");
						return getUrl(Constantes.MENSAJE_ADJUNTAR_ARCHIVOS);
					}
				} else {
					logger.debug("Es usuario es SIN Certificado, no puede adjuntar documentaci�n.");
				}
			}
		} catch (Exception e) {
			logger.error("Error en el proceso de pago y presentacion simplificada.", e);
			request.setAttribute("resultInfo", new ResultInfo(true, "", e.getMessage()));
			return getUrl(Constantes.MOSTRAR_ERROR);
		}
		if (esTarjetaFuncionario == null || !esTarjetaFuncionario.equals("pagoTarjeta")) {
			url = getUrl(Constantes.PRESENTACION_FUNCIONARIO_FRAME);
		}

		
		/**
		 * smc017 - Comprobamos el estado de su autorizacion y redireccionamos a
		 * documentos adjuntos eliminando previamente el adjunto con la informacion de pago desactualizada 
		 * para que cambie el Adjunto por el del nuevo metodo de pago seleccionado.
		 **/
		ConsultaLiquidacionInfo liquidacionActual = getLiquidacionBLJBean(request).getUltimaEvolucion(userProfile, liquidacion.getIdLiquidacion());
		if(liquidacionActual.getIban() == null) {liquidacionActual.setIban("");};
		if(liquidacionAnterior.getIban() == null) {liquidacionAnterior.setIban("");};
		if(liquidacionActual.getNifSujetoPasivo() == null) {liquidacionActual.setNifSujetoPasivo("");};
		if(liquidacionAnterior.getNifSujetoPasivo() == null) {liquidacionAnterior.setNifSujetoPasivo("");};
		if(liquidacionActual.getNombreUsuario() == null) {liquidacionActual.setNombreUsuario("");};
		if(liquidacionAnterior.getNombreUsuario() == null) {liquidacionAnterior.setNombreUsuario("");};
		
		String idLiquidacion = liquidacion.getIdLiquidacion();
		String modelo = idLiquidacion.substring(0, 3);
		String version = idLiquidacion.substring(3, 4);
		LiquidacionInfo liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
		String concepto = liquidacionInfo.getConcepto();
		DocumentoAdjuntoInfoSet adjuntos_modadj = getLiquidacionBLJBean(request).getTiposDocumentosAdjuntos(idLiquidacion, modelo, version, concepto);
		
		if((!liquidacionActual.getIban().equals(liquidacionAnterior.getIban()) || !liquidacionActual.getNifSujetoPasivo().equals(liquidacionAnterior.getNifSujetoPasivo())
				|| !liquidacionActual.getNombreUsuario().equals(liquidacionAnterior.getNombreUsuario()) || liquidacionActual.getIdEstadoActual() == 8) 
			&& 
			(adjuntos_modadj != null && adjuntos_modadj.getArrayList() != null && adjuntos_modadj.getArrayList().size() > 0) ) {

			url = getUrl("MOSTRAR_ADJUNTAR_DOCUMENTACION");		
			getLiquidacionBLJBean(request).eliminarAdjuntoEnCambioAutorizacion(idLiquidacion);
			getComboTiposDocumentosAdjuntos(request, liquidacionInfo, idLiquidacion, modelo, version, concepto);		
		}
		

		return url;
	}
    
    private boolean modificarAutorizacion(UserProfileInfo userProfile, String entidadFinanciera, String codigoIban, CensoRespInfo censoRespInfo, CensoRespInfo censoRespInfoCotitular) throws Exception{
        boolean modificar=false;
        
        if ((userProfile.getAutorizacion().getIdAutorizacion()==null) 
                || ((entidadFinanciera!=null && !userProfile.getAutorizacion().getCodEntidad().equals(entidadFinanciera.toUpperCase()))
                       ||((codigoIban != null && !codigoIban.equals("")) && userProfile.getAutorizacion().getCodigoIban() != null && !userProfile.getAutorizacion().getCodigoIban().equals(codigoIban.toUpperCase()))
                       ||(!userProfile.getAutorizacion().getNombrePagador().equals(censoRespInfo.getSujeto()))) 
               ||(!userProfile.getAutorizacion().getNifPagador().equals(censoRespInfo.getNif()))
               ||(censoRespInfoCotitular != null && Utilidades.estaVacia(userProfile.getAutorizacion().getNifPagador2()))
               ||(censoRespInfoCotitular != null && Utilidades.estaVacia(userProfile.getAutorizacion().getNombrePagador2()))
               ||(!Utilidades.estaVacia(userProfile.getAutorizacion().getNifPagador2()) && censoRespInfoCotitular == null)
               ||(!Utilidades.estaVacia(userProfile.getAutorizacion().getNombrePagador2()) && censoRespInfoCotitular == null)
               ||(!Utilidades.estaVacia(userProfile.getAutorizacion().getNifPagador2()) && !userProfile.getAutorizacion().getNifPagador2().equals(censoRespInfoCotitular.getNif()))
               ||(!Utilidades.estaVacia(userProfile.getAutorizacion().getNombrePagador2()) && !userProfile.getAutorizacion().getNombrePagador2().equals(censoRespInfoCotitular.getSujeto()))){
            
            modificar = true;
            
        }
            
        
        return modificar;
    }

    private String mostrarAutorizacion(HttpServletRequest request, 
            UserProfileInfo userProfile, boolean datosModif){

        ResultInfo resultInfo = null;
        byte[] oPDF=null;
        String esTarjeta = userProfile == null ? null : userProfile.getAutorizacion() == null ? null : userProfile.getAutorizacion().getTarjeta();
		String esNRC = userProfile == null ? null : userProfile.getAutorizacion() == null ? null : userProfile.getAutorizacion().getNrc();
		boolean esJustificantePagoRecibido = userProfile == null ? null : userProfile.getAutorizacion() == null ? null : userProfile.getAutorizacion().isJustificantePagoRecibido();
		
        try{
            ConsultaLiquidacionInfo consultaLiquidacion = (ConsultaLiquidacionInfo)request.getSession().getAttribute("liquidacionInfo");
            LiquidacionInfo liquidacion = getLiquidacionBLJBean(request).getLiquidacion(userProfile,consultaLiquidacion.getIdLiquidacion());
            if (datosModif){
                if (liquidacion.getIdEstadoActual()!=Constantes.ESTADO_PENDIENTE_FIRMA){
                    consultaLiquidacion.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_FIRMA);
                    liquidacion.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_FIRMA);
                    resultInfo=completarDatosEvolucion(request,userProfile, new PresentacionInfo(liquidacion));
                    resultInfo=getLiquidacionBLJBean(request).setLiquidacion(userProfile,liquidacion);
                    if (resultInfo.getError()){
                        return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                }
                if(esTarjeta != null && esTarjeta.equals("true")){//Tarjeta
                    //Parche para que no pete el PL
                    userProfile.getAutorizacion().setCodEntidad(null);
                    userProfile.getAutorizacion().setCodSucursal(null);
                    userProfile.getAutorizacion().setCuenta(null);
                    userProfile.getAutorizacion().setDc(null);
                    userProfile.getAutorizacion().setCodigoIban(null);
                    consultaLiquidacion.setTarjeta("1");
                } else if(esNRC != null && !esNRC.isEmpty()){
                	//Si no viene el justificante sera el modo de pago por NRC
					if(!esJustificantePagoRecibido) {
						consultaLiquidacion.setTarjeta("2");
					}else {
						//Si viene el justificante sera el modo de pago por justificante en el cual se genera un NRC ficticio
						consultaLiquidacion.setTarjeta("3");
					}
				} else {
                    consultaLiquidacion.setTarjeta("0");
				}
                
                //consultamos si el pagador/presentador es representante del sujPas
                oPDF = generaAutorizacion (request,userProfile,consultaLiquidacion,datosModif); 
                
            	String firma  = (String)request.getAttribute("idFirma");
             	
             	if (null != firma && !"".equals(firma)){
             		liquidacion.setIdDocumentoFirma(Double.parseDouble(firma));
             		consultaLiquidacion.setIdTransaccionLiquidacion(Double.parseDouble(firma));
             	}else{
             		logger.debug("mostrarAutorizacion: No se ha podido recuperar el par�metro idFirma ni de sesi�n ni del request.");
             	}
             	if (liquidacion.getTotalIngresar()>0.0){
                    if(esTarjeta != null && esTarjeta.equals("true")){//Tarjeta
                        //Que le enviamos a Tarjeta 
                        liquidacion.setCodEntidadObject(null);
                        liquidacion.setCodigoIban(null);
                        liquidacion.setTarjeta(userProfile.getAutorizacion().getTarjeta());
                        liquidacion.setFechaCadTarjeta(userProfile.getAutorizacion().getFechaCadTarjeta());
                        //Se lo paso todo a null
                        userProfile.getAutorizacion().setCodEntidad(null);
                        userProfile.getAutorizacion().setCodigoIban(null);
                        //Indicador de que es tarjeta que le tendremos que pasar a PALTAUTEX
                        consultaLiquidacion.setTarjeta("1");
                
                    }else if(esTarjeta != null && esTarjeta.equals("false")){//CCC
                        liquidacion.setCodEntidad(Integer.parseInt(userProfile.getAutorizacion().getCodEntidad()));
                        liquidacion.setCodigoIban(userProfile.getAutorizacion().getCodigoIban());
                        liquidacion.setTarjeta(null);
                        liquidacion.setFechaCadTarjeta(null);
                        //Indicador de que es tarjeta que le tendremos que pasar a PALTAUTEX
                        consultaLiquidacion.setTarjeta("0");
                    } else if (esNRC!=null ? !esNRC.isEmpty() : false) {//NRC
						liquidacion.setCodEntidad(Integer.parseInt(userProfile.getAutorizacion().getCodEntidad()));
						liquidacion.setNrc(userProfile.getAutorizacion().getNrc());
						liquidacion.setTarjeta(null);
						liquidacion.setFechaCadTarjeta(null);
						userProfile.getAutorizacion().setCodigoIban(null);
						liquidacion.setCodigoIban(null);
						// Indicador de que es tarjeta que le tendremos que pasar a PALTAUTEX (indica el metodo de pago realizado)
						// 0: CCC
						// 1: Tarjeta
						// 2: NRC
						// 3: Justificante Pago (NRC Ficticio)
						if(!esJustificantePagoRecibido) {
							consultaLiquidacion.setTarjeta("2");
						}else {
							consultaLiquidacion.setTarjeta("3");
						}
                    }
                }
                
                resultInfo = actualizarEvolucionBDFuncionario(request, userProfile, consultaLiquidacion,liquidacion);

                long idEvolucionActualizado = getLiquidacionBLJBean(request).obtenerUltimoIdEvolucion(userProfile,liquidacion);
                logger.debug("Obtengo el id de Evolucion despues de actualizar la autorizaci�n para Empleado P�blico y lo meto en sesion: " + idEvolucionActualizado);                
                consultaLiquidacion.setIdEvolucion(idEvolucionActualizado);
                
                request.getSession().setAttribute("liquidacionInfo",consultaLiquidacion);
                
            }else{
            	// Colocamos en el request los par�metros para mostrar de nuevo el PDF de la autorizaci�n
            	request.setAttribute("idFirma", (String)request.getSession().getAttribute("idFirma"));  
            	request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, consultaLiquidacion.getIdLiquidacion());
            }
            if (resultInfo !=null  && resultInfo.getError()){
                return getUrl(Constantes.MOSTRAR_ERROR);
            }                               
            
            request.setAttribute("codigoIban", userProfile.getAutorizacion().getCodigoIban());
            request.getSession().setAttribute("userProfile",userProfile);
            
            return getUrl(Constantes.PRESENTACION_FUNCIONARIO_FRAME);
            
        }catch (Exception e) {
            logger.error("Error en el proceso de pago y presentacion simplificada.", e);                
            return getUrl(Constantes.MOSTRAR_ERROR);
        }        
    }
    
    
    /** NECESARIO **/

    private void descargaIdFirma(HttpServletRequest request, 
        HttpServletResponse response, UserProfileInfo userProfile){

        String sId = null;
        byte[] oPDF = null;
        sId = (String) request.getAttribute("id")!=null?(String) request.getAttribute("id"):request.getParameter("id");
        logger.debug("request.getAttribute(\"idFirma\"):"+request.getAttribute("id"));
        logger.debug("request.getParameter(\"idFirma\"):"+request.getParameter("id"));
                        
        if (sId == null) {
            throw new ApplicationError(new ResultInfo(true, "PDF003", 
                getSessionJBean(request).getLiteral("PDF003")), 
                new Exception("No estaba el ID en el request "));
        } else {
            
                try {                	
                    if (((HashMap<String, DatosAFirmar>) request.getSession().getAttribute(
                            Constantes.LISTA_IDS)).get(sId) == null) {
                        throw new ApplicationError(new ResultInfo(true, "PDF004", 
                            getSessionJBean(request).getLiteral("PDF004")), 
                            new Exception("No estaba el ID en la lista de IDs "));
                    }
                } catch (Exception e) {
                    logger.error("Error chequeando si el ID est� en la lista de IDs.", e);
                    throw new ApplicationError(new ResultInfo(true, "PDF004", 
                        getSessionJBean(request).getLiteral("PDF004")), e);
                }
                try {
                    ClienteFirma clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA));
                    oPDF = clienteFirma.getDocumento(Double.parseDouble(sId));
                } catch (Exception e) {
                    throw new ApplicationError(new ResultInfo(true, "PDF008", 
                        getSessionJBean(request).getLiteral("PDF008")), e);
                }
            }
            try {
                enviaFichero(oPDF, response, sId);
                request.getSession().setAttribute("idFirma",sId);
                String expedirAutorizacion = (String) request.getAttribute("expedirAutorizacion")!=null?(String) request.getAttribute("expedirAutorizacion"):request.getParameter("expedirAutorizacion");
                if (!Utilidades.estaVacia(expedirAutorizacion) && expedirAutorizacion.equals("on"))
                    request.getSession().setAttribute("autorizacionImpresa",expedirAutorizacion);
            } catch (Exception e) {
                throw new ApplicationError(new ResultInfo(true, "PDF005", 
                    getSessionJBean(request).getLiteral("PDF005")), e);
            }
            
             
    }
    
    /**
     * Genera y devuelve el PDF con el atributo de impresi�n.
     * @param request
     * @param response
     * @param userProfile
     */
    private void generaAutorizacionImpresion(HttpServletRequest request, 
            HttpServletResponse response, UserProfileInfo userProfile){

            String sId = null;
            byte[] oPDFImpresion = null;
            sId = (String) request.getAttribute("id")!=null?(String) request.getAttribute("id"):request.getParameter("id");
            logger.debug("request.getAttribute(\"idFirma\"):"+request.getAttribute("id"));
            logger.debug("request.getParameter(\"idFirma\"):"+request.getParameter("id"));
                            
            if (sId == null) {
                throw new ApplicationError(new ResultInfo(true, "PDF003", 
                    getSessionJBean(request).getLiteral("PDF003")), 
                    new Exception("No estaba el ID en el request "));
            } else {
                
                    try {                	
                        if (((HashMap<String, DatosAFirmar>) request.getSession().getAttribute(
                                Constantes.LISTA_IDS)).get(sId) == null) {
                            throw new ApplicationError(new ResultInfo(true, "PDF004", 
                                getSessionJBean(request).getLiteral("PDF004")), 
                                new Exception("No estaba el ID en la lista de IDs "));
                        }
                    } catch (Exception e) {
                        logger.error("Error chequeando si el ID est� en la lista de IDs.", e);
                        throw new ApplicationError(new ResultInfo(true, "PDF004", 
                            getSessionJBean(request).getLiteral("PDF004")), e);
                    }
                    try {
                        ClienteFirma clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA));
                        oPDFImpresion = clienteFirma.getDocumento(Double.parseDouble(sId));
                        // Le a�adimos la accion de impresion a un duplicado de la Autorizaci�n.
                        ModificacionPDF generadorPDF2 = new ModificacionPDF();
                        oPDFImpresion = generadorPDF2.aniadirImpresionPDF(oPDFImpresion);
                    } catch (Exception e) {
                        throw new ApplicationError(new ResultInfo(true, "PDF008", 
                            getSessionJBean(request).getLiteral("PDF008")), e);
                    }
                }
                try {
                    enviaFichero(oPDFImpresion, response, sId);
                    request.getSession().setAttribute("idFirma",sId);
                    String expedirAutorizacion = (String) request.getAttribute("expedirAutorizacion")!=null?(String) request.getAttribute("expedirAutorizacion"):request.getParameter("expedirAutorizacion");
                    if (!Utilidades.estaVacia(expedirAutorizacion) && expedirAutorizacion.equals("on"))
                        request.getSession().setAttribute("autorizacionImpresa",expedirAutorizacion);
                } catch (Exception e) {
                    throw new ApplicationError(new ResultInfo(true, "PDF005", 
                        getSessionJBean(request).getLiteral("PDF005")), e);
                }
        }

    /**
     * Comienza el proceso de pago con tarjeta. Si todo va bien, la firma, redirigimos a la pagina jsp que hace
     * la llamada al sistema Pasat 
     * @author VAS000
     * @param request
     * @param response
     * @param userProfile
     * @return urlDst
     * @throws Exception 
     * @throws Exception
     * 
     */
    
    private String inicioFirmaPagoTarjeta(HttpServletRequest request, UserProfileInfo userProfile) throws Exception{
        
        String sId = null;
        long idEvolucion;
        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        String firma = null;
        //String sIdLiquidacion = null;
        LiquidacionInfo liquidacionInfo = null;
        PresentacionInfo presentacionInfo = null;
        EvolucionLiquidacionInfo evolucionLiquidacionInfo = null;
        EvolucionLiquidacionInfo evolucionLiquidacionInfo909 = null;
        EvolucionLiquidacionInfo evolInfo=null;
        EvolucionLiquidacionInfo evolInfo909=null;
        ResultInfo errorEnProceso = null;
        String codigoTerritorial = null;
        request.setAttribute("pago",request.getParameter("pago"));
        sId = (String)request.getSession().getAttribute("idFirma");
        String esTarjeta = request.getParameter("esTarjeta");
        String idTransaccionServidor909 = null;
        
        if(esTarjeta == null){
            esTarjeta = "";
        }
        int bloqueado=1;
        RespuestaPagoInfo respuestaConsultaPrevia = null;  
        int codEntFin;
        //Borramos los parametros utilizados en la pagina anterior
        request.getSession().removeAttribute("codigoTerritorialOficinaPresentacion");
        request.getSession().removeAttribute("literalOficinaPresentacion");
        request.getSession().removeAttribute("literalOficinaPresentacionDocumento");
        request.getSession().removeAttribute("comboOficinaPresentacion");
        request.getSession().removeAttribute("codigosTerritorialesHash");
        request.getSession().removeAttribute("id_"+request.getSession().getAttribute("idFirma"));
        request.getSession().removeAttribute("idFirma");
        
        codigoTerritorial = request.getParameter("codigoTerritorial");
        idEvolucion=Long.parseLong(request.getParameter("idEvolucion"));
         
        try{
            
            logger.trace("##########################################################################################");
            logger.trace("##########################################################################################");
            logger.trace("####################### PROCESO DE PAGO PRESENTACION SIMPLIFICADO TARJETA ################");
            logger.trace("##########################################################################################");
            logger.trace("##########################################################################################");
            
                                                    
            /**********************/            
            /** PROCESO DE FIRMA **/
            /**********************/
            logger.trace("*************************************");
            logger.trace("COMIENZO DEL PROCESO DE FIRMA TARJETA");
            logger.trace("*************************************");
            
            ArrayList arrayListNumeroDocumentos = new ArrayList();
            
            try{ 
            	arrayListNumeroDocumentos = (ArrayList) request.getSession().getAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE);
            	//TODO Fernando Piedra. Se comprueba antes de continuar si alguna de las liquidaciones ya ha sido introducida en SUR.                
                ArrayList<String> documentosEnSur = new ArrayList<String>(); 
                boolean algunaPagada = false;
    	        for (int i = 0; i < arrayListNumeroDocumentos.size(); i++) {
    	        	boolean esPagada = false;
    	        	String idLiquidacion = (String) arrayListNumeroDocumentos.get(i);    	           
    		    	String modelo = idLiquidacion.substring(0, 3);
    		    	String version = idLiquidacion.substring(3,4);
    		    	String numeroDocumento = idLiquidacion.substring(4);
    		    	
    		    	//Antes de nada, obtengo el concepto del documento:
    		    	liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
                    String conceptoConsulta = null;
                    if(liquidacionInfo != null){
                        conceptoConsulta = Utiles.controlaConcepto(liquidacionInfo.getConcepto());
                    }
    		    	
    	    		esPagada = getLiquidacionBLJBean(request).compruebaLiquidacionPagada(modelo,version,numeroDocumento,conceptoConsulta);
    	    		if(esPagada){
    	    			documentosEnSur.add(idLiquidacion);
    	    			algunaPagada = true;
    	    		}
                }
        		if(algunaPagada){    			
        			StringBuffer lista = new StringBuffer();
        			for (int i = 0; i < documentosEnSur.size(); i++){    				
        				String documento = (String)documentosEnSur.get(i);
        				lista.append(" " + documento + ";");
        			}
        			logger.error("De los documentos seleccionados, hay " + documentosEnSur.size() + " documento/s que ya se encuentra/n en SUR, y es/son: " + lista.toString());
        			ResultInfo resultError = new ResultInfo();
        			resultError.setError(true);
        			if(documentosEnSur.size() > 1){
        				resultError.setMessage("Alguno de los documentos que est� intentando pagar/presentar existen en el sistema. No es posible realizar su pago/presentaci�n telem�tico. Contacte con el servicio de atenci�n al usuario (CEIS) que podr� encontrar en la p�gina de contacto de nuestra Oficina Virtual.");
        			}else{
        				resultError.setMessage("El documento que est� intentando pagar/presentar existe en el sistema. No es posible realizar su pago/presentaci�n telem�tico. Contacte con el servicio de atenci�n al usuario (CEIS) que podr� encontrar en la p�gina de contacto de nuestra Oficina Virtual.");
        			}
        			request.setAttribute("resultInfo", resultError);
        			return getUrl(Constantes.MOSTRAR_ERROR);
        		}
            }catch (NullPointerException ex){
                ResultInfo resultInfo = new ResultInfo(true,"","Error: Se ha Firmado/Pagado/Presentado por otra ventana");
                request.setAttribute("resultInfo",resultInfo);
                return getUrl(Constantes.MOSTRAR_ERROR);
            }
            boolean hayUrlRecibo=false;
            for (int i=0; i<arrayListNumeroDocumentos.size();i++){
                String sIdLiquidacion = (String) arrayListNumeroDocumentos.get(i);
                //Obtenemos el liquidacion Info correspondiente y creamos el presentacion info para despues
                liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, sIdLiquidacion);
                liquidacionInfo.setJustificante(liquidacionInfo.getIdLiquidacion());                                                            
                presentacionInfo = new PresentacionInfo(liquidacionInfo); 
                presentacionInfo.setIdEvolucion(idEvolucion);
                if (!hayUrlRecibo){
                    hayUrlRecibo= devolverUrlRecibo(request, userProfile, liquidacionInfo);                    
                }
                if (liquidacionInfo.getIdEstadoActual()==Constantes.ESTADO_PENDIENTE_FIRMA){            
                    liquidacionInfo.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_FIRMA);           
                    presentacionInfo.setLiteralEstadoActual(getSessionJBean(request).getLiteral(Constantes.LITERAL_PENDIENTE_FIRMA));
                    
                    if (idEvolucion == getLiquidacionBLJBean(request).obtenerUltimoIdEvolucion(userProfile,liquidacionInfo)){
                    
                        //Evitar firmar si no hay permisos
                        //Comprobar que este activo el modelo de la autoliquidacion
                                
                        if (sId == null) {
                            errorEnProceso = new ResultInfo(true, "PDF003", getSessionJBean(request).getLiteral("PDF003"));
                            throw new Exception("No estaba el ID en el request ");
                        } else {     
                            //Si el parametro es -1 y no estamos en entorno de desarrollo.                  
                                if("-1".equals(sId)){
                                    errorEnProceso = new ResultInfo(true, "PDF003", getSessionJBean(request).getLiteral("PDF003"));
                                    logger.error("***ERROR EN EL PROCESO DE FIRMA*** El parametro identificador de firma recibido es: "+sId+" ,no se invocara al EJB de firma.");
                                    throw new Exception("Parametro identificador de firma incorrecto = -1");                            
                                }else{
                                    try {
                                        if (((HashMap<String, DatosAFirmar>)request.getSession().getAttribute(Constantes.LISTA_IDS)).get(sId) == null) {
                                            errorEnProceso = new ResultInfo(true, "PDF004", getSessionJBean(request).getLiteral("PDF004"));
                                            throw new Exception("No estaba el ID en la lista de IDs ");
                                        }
                                    } catch (Exception e) {
                                        errorEnProceso = new ResultInfo(true, "PDF004", getSessionJBean(request).getLiteral("PDF004"));
                                        logger.error("inicioFirmaPagoTarjeta: Error chequeando si el ID est� en la lista de IDs ");
                                        throw e;
                                    }
                                    try { 
                                        ClienteFirma clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA));
                                        //Recupero el objeto certificado de Session 
                                        CertificadoDigital cdAutenticado = (CertificadoDigital) request.getSession().getAttribute("__Certificado");
                                        firma = request.getParameter("firma");
                                        //Obtenfo la informacion del Certificado utilizado en la firma
                                        
                                        CertificadoDigital cdFirmante = null;
                                        String certificadoFirmante = "";
                                        byte[] datosFirmados = clienteFirma.getDocumento(Double.parseDouble(sId));
                                        Vector<InformacionFirmante> firmantes = clienteFirma.verificarFirmaYExtraerFirmantes(Base64.decode(firma),datosFirmados);
                                        if(!firmantes.isEmpty()){
                                            InformacionFirmante firmante = firmantes.get(0);
                                            cdFirmante = firmante.getCertificadoDigital();
                                            certificadoFirmante = firmante.getCertificado();
                                        }
                                        
                                        logger.debug("cdAutenticado:"+cdAutenticado);
                                        logger.debug("cdFirmante:"+cdFirmante);
                                        if(cdAutenticado.equals(cdFirmante) || ControladorSimulador.esSimuladorActivoUsuario()){
                                            //Se realiza el comit de la firma.
                                            firma = request.getParameter("firma");
                                            clienteFirma.generarFirma(Double.parseDouble(sId), firma, certificadoFirmante, sIdLiquidacion, null);                                         
                                        }else {
                                            errorEnProceso = new ResultInfo(true, "PDF011", getSessionJBean(request).getLiteral("PDF011"));
                                            throw new Exception("El certificado utilizado en la firma no coincide con el utilizado en la autenticacion de la aplicaci�n.");
                                        }
                                        
                                    } catch (FirmaException e) {
                                        errorEnProceso = new ResultInfo(true, "PDF007", getSessionJBean(request).getLiteral("PDF007"));
                                        throw  e;
                                    }
                                }
                                
                                // Cambio el estado de la autoliquidacion
                                liquidacionInfo.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                                liquidacionInfo.setIdDocumentoFirma(Double.parseDouble(sId));
                                if (liquidacionInfo.getLiquidacionInfo909() != null && idTransaccionServidor909 != null) {
                                    //Id firma para carta de pago 909
                                    liquidacionInfo.getLiquidacionInfo909().setIdEstadoActual(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                                    evolucionLiquidacionInfo909 = new EvolucionLiquidacionInfo();
                                    evolucionLiquidacionInfo909.setIdTipoEstado(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                                    evolucionLiquidacionInfo909.setIdLiquidacion(liquidacionInfo.getLiquidacionInfo909().getIdLiquidacion());
                                    evolucionLiquidacionInfo909.setIdUsuario(userProfile.getIdUsuario());
                                    evolucionLiquidacionInfo909.setFechaEstado(new java.util.Date());
                                    evolInfo909=getPresentacionBLJBean(request).setPresentacion(userProfile, liquidacionInfo.getLiquidacionInfo909(), evolucionLiquidacionInfo909);
                                }                  
                                //vuelvo a actualizar la presentacion con los ultimos cambios
                                presentacionInfo = new PresentacionInfo(liquidacionInfo);
                                evolucionLiquidacionInfo = new EvolucionLiquidacionInfo();
                                evolucionLiquidacionInfo.setIdTipoEstado(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                                evolucionLiquidacionInfo.setIdLiquidacion(liquidacionInfo.getIdLiquidacion());
                                evolucionLiquidacionInfo.setIdUsuario(userProfile.getIdUsuario());
                                evolucionLiquidacionInfo.setFechaEstado(new java.util.Date());
                                evolInfo=getPresentacionBLJBean(request).setPresentacion(userProfile, liquidacionInfo, evolucionLiquidacionInfo);
                                presentacionInfo.setIdEvolucion(evolInfo.getIdEvolucion());
                        
                        } 
                        //El documento ya ha sido firmado ==> lo elimino de sesi�n
                        ((HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS)).remove(sId);

                        if (null != request.getSession().getAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE)){
                                request.getSession().removeAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE); 
                        }                                       
                    }
                    else{
                        ResultInfo resultFirma = new ResultInfo(true,"","Error: Se est� firmando esta liquidaci�n por otra ventana");
                        request.setAttribute("resultInfo",resultFirma);
                        return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                }
            }//end for
            //FIN PROCESO FIRMA         
            
            //TRASLADO ESTE BLOQUEO A MostrarAvisoComponerMensaje
            //Bloquear documento y Actualizar la evolucion del documento, insertando dos estado un Intento Pago y un Pendiente Pagar/Presentar 
            /*if(presentacionInfo != null){
                bloqueado = bloqueoActEvolucion(request,presentacionInfo, Constantes.GESTION_PRES_ERROR,"SIMPLIFICADA");
                if (bloqueado != 0){
                    ResultInfo resultInfo = new ResultInfo(true,Constantes.GESTION_PRES_ERROR,getSessionJBean(request).getLiteral(Constantes.GESTION_PRES_ERROR));
                    throw new ApplicationError(resultInfo, new Exception("Error al bloquear el documento "+presentacionInfo.getIdLiquidacion()+" en el inicio pago con tarjeta."));
    
                }
            }else{ 
                logger.debug("inicioFirmaPagoTarjeta: No existe ninguna liquidacion para pagar con tarjeta, presentacionInfo"+presentacionInfo);
                request.setAttribute("resultInfo", new ResultInfo(true,"","No existe ninguna liquidacion para pagar con tarjeta"));
                return urlDst;
            }*/   
            
            request.getSession().setAttribute("order", liquidacionInfo.getLiquidacionInfo909().getIdLiquidacion());
            request.getSession().setAttribute("esTarjeta", esTarjeta);
            //Consultamos la direccion del IECISA en base de datos
            /* Miramos si el entorno en que estamos es Pruebas, ya que en
             * ese caso hemos de comprobar si hemos accedido desde red corporativa 
             * o desde la calle, para saber la url a d�nde redireccionar 
             */ 
            if (entorno.equals("P"))
            {
                try{
                    String direccionCliente = request.getHeader("X-FORWARDED-FOR");
                    VerificadorProcedenciaCliente verProcClient = new VerificadorProcedenciaCliente();
                    boolean entradaRedCorporativa = verProcClient.estaEnRedCorporativa(direccionCliente);
                    if (entradaRedCorporativa)
                        codEntFin = Constantes.CODENTFIN_URLPAGOTARJETA_WEB_CORP; //0001
                    else
                        codEntFin = Constantes.CODENTFIN_URLPAGOTARJETA_WEB; //0000
                }catch (Exception e){
                    throw new Exception ("Error en la obtencion de la url de salida." +e);
                }
            }else{
                codEntFin = Constantes.CODENTFIN_URLPAGOTARJETA_WEB;
                
            }
            EntidadFinancieraInfo entidadFinancieraInfo = getPresentacionBLJBean(request).getEntidadFinanciera(userProfile,codEntFin);
            request.setAttribute("urlPagoWebTarjeta", entidadFinancieraInfo.getUrlConexion());
            
            urlDst = getUrl(Constantes.LLAMADA_SERVICIO_PAGO_TARJETA_WEB_SIMPLIFICADA);
            
            
            return urlDst;      
            
            
        } catch (Exception e) {
            try{
                //Si el documento fue bloqueado procedemos al desbloqueo
                if(bloqueado==0){
                    bloqueado = desbloquear(request, presentacionInfo.getIdLiquidacion());
                }
                logger.error("Error en inicioPagoTarjeta", e);  
            }catch (Exception ex){
                logger.error(e.getMessage(), e);
                request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                urlDst = getUrl(Constantes.MOSTRAR_ERROR);
            }
            return urlDst;                                      
        }
        
    }
    
    /**
     * Redirecciona el resultado de pago con tarjeta en Simplificada a la jsp de resultado. 
     * @author VAS000
     * @param request
     * @param response
     * @param userProfile
     * @return urlDst
     * @throws Exception 
     * @throws Exception
     * 
     */
    
    private String mostrarResultadoFirmaPagoTarjeta(HttpServletRequest request, UserProfileInfo userProfile) throws Exception{
        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        //Recojo los parametros que me vienen en el request que necesita la jsp
        String pago = request.getParameter("pago");
        String nombreEntidadFinanciera = request.getParameter("nombreEntidadFinanciera");
        String idLiquidacion = request.getParameter("idLiquidacion");
        String pagoTarjeta = request.getParameter("pagoTarjeta");
        String ccc = request.getParameter("ccc");
        String mensajeError = request.getParameter("mensajeError");
        String errorEnProceso = request.getParameter("errorEnProceso");
        PresentacionInfo presentacionInfo = null;
        String sinCertificado = request.getParameter("sinCertificado");
        String consultaSinCer = request.getSession().getAttribute("consultaSinCertificado")!=null?
                    (String)request.getSession().getAttribute("consultaSinCertificado"):"";
        
        logger.debug("mostrarResultadoFirmaPagoTarjeta: idLiquidacion" + idLiquidacion);
        try{
            //Intento recoger a traves del idLiquidacion la liquidacion de BD
            if(pagoTarjeta != null && pagoTarjeta.equals("true")){
                //Vamos a tratar independiente si es pago Tarjeta
                //Debo de recuperar a traves del idLiquidacion el objeto presentacionInfo que esta alojado en BD
                LiquidacionInfo liquidacionInfo = getPresentacionBLJBean(request).getWebLiquidacion(userProfile,idLiquidacion);
                if(liquidacionInfo != null && liquidacionInfo.getResultInfo()!= null && liquidacionInfo.getResultInfo().getError()){
                    logger.debug("mostrarResultadoFirmaPagoTarjeta: No existe ninguna liquidacion con el idLiquidacion " + idLiquidacion + " en sn_webliq");
                    liquidacionInfo = getPresentacionBLJBean(request).getLiquidacion(userProfile,idLiquidacion);
                    if(liquidacionInfo != null && liquidacionInfo.getResultInfo()!= null && liquidacionInfo.getResultInfo().getError()){
                        logger.error("mostrarResultadoFirmaPagoTarjeta: No existe ninguna liquidacion con el idLiquidacion " + idLiquidacion + " en sn_webliq");
                        throw new Exception("No existe liquidacion");
                    }
                }
                presentacionInfo = new PresentacionInfo(liquidacionInfo);
                TipoEstadoLiquidacionInfo estadoLiquidacionInfo = getPresentacionBLJBean(request).getTipoEstadoLiquidacion(userProfile, presentacionInfo.getIdEstadoActual());
                presentacionInfo.setLiteralEstadoActual(getSessionJBean(request).getLiteral(estadoLiquidacionInfo.getLiteral()));
                logger.debug("mostrarResultadoFirmaPagoTarjeta: PresentacionInfo de BD: " + presentacionInfo);
                
                //Recupero e introduzco el userProfile
                long codUsuario = liquidacionInfo.getIdUsuario();
                userProfile = getContratoBLJBean(request).getUserProfile(String.valueOf(codUsuario));
                request.getSession().setAttribute("userProfile",userProfile);
                
                //Nuevo control del error, porque perdiamos la session y habia que pasarlo por parametro
                if(errorEnProceso != null && errorEnProceso.equals("true")){
                    request.getSession().setAttribute("errorEnProceso",new ResultInfo(true,"",""));
                }else{
                    request.getSession().setAttribute("errorEnProceso",new ResultInfo(false,"",""));
                } 
                request.getSession().setAttribute("presentacionInfo",presentacionInfo);
                request.setAttribute("pago",pago);
                request.setAttribute("nombreEntidadFinanciera",nombreEntidadFinanciera);
                request.setAttribute("pagoTarjeta",pagoTarjeta);
                request.setAttribute("ccc",ccc);
                request.setAttribute("mensajeError",mensajeError);
                //Lo borraremos cuando lo utilicemos
                request.getSession().setAttribute("errorTarjetaDesc",presentacionInfo.getMotivoLiquidacion());
                //Enviar� a una pantalla distinta cuando sea un pago sin Certificado
                if( ( (sinCertificado != null && sinCertificado.equals("sinCertificado"))
                        && ( pagoTarjeta != null && pagoTarjeta.equals("true") ) ) || consultaSinCer.equals("true") ){
                    request.setAttribute("pagoTarjeta",pagoTarjeta);
                    request.setAttribute("sinCertificado","sinCertificado");                    
                    urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_RESULTADO_SIN_CER);
                }else{
                    urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_RESULTADO);
                }   
            }   
        }catch(Exception e){
            logger.error(e.getMessage(), e);
            urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        }
        
        return urlDst;
    }
    
    /** METODO QUE EJECUTA UNA OPERATIVA COMPLETA DE FIRMA PAGO Y PRESENTACION MOSTRANDO LOS RESULTADOS CORRESPONDIENTES **/
    
    private String firmaPagoPresentacion(HttpServletRequest request, UserProfileInfo userProfile){

        String sId = null;
        String idTransaccionServidor909 =  null;
        long idEvolucion;
        String urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_RESULTADO);
        String firma = null;
        //String sIdLiquidacion = null;
        LiquidacionInfo liquidacionInfo = null;
        PresentacionInfo presentacionInfo = null;
        EvolucionLiquidacionInfo evolucionLiquidacionInfo = null;
        EvolucionLiquidacionInfo evolucionLiquidacionInfo909= null;
        EvolucionLiquidacionInfo evolInfo=null;
        EvolucionLiquidacionInfo evolInfo909=null;
        String codigoTerritorial = null;
        ResultInfo errorEnProceso = null;
        request.setAttribute("pago",request.getParameter("pago"));
        sId = (String)request.getSession().getAttribute("idFirma"); 
        String esTarjeta = request.getParameter("esTarjeta");
        if(esTarjeta == null){
            esTarjeta = "";
        }
        
        String pagoTarjeta = request.getParameter("pagoTarjeta");
        logger.debug("firmaPagoPresentacion: Es :" + pagoTarjeta);
        //String fecCadTarjeta = request.getParameter("fecCadTarjeta");
                            
        //Borramos los parametros utilizados en la pagina anterior
        request.getSession().removeAttribute("codigoTerritorialOficinaPresentacion");
        request.getSession().removeAttribute("literalOficinaPresentacion");
        request.getSession().removeAttribute("literalOficinaPresentacionDocumento");
        request.getSession().removeAttribute("comboOficinaPresentacion");
        request.getSession().removeAttribute("codigosTerritorialesHash");
        request.getSession().removeAttribute("id_"+request.getSession().getAttribute("idFirma"));
        request.getSession().removeAttribute("idFirma");
        if(pagoTarjeta != null && pagoTarjeta.equals("pagoTarjeta")){
            request.getSession().removeAttribute("nombreEntidadFinanciera");
        }
        
        codigoTerritorial = request.getParameter("codigoTerritorial");
        idEvolucion=Long.parseLong(request.getParameter("idEvolucion"));
        logger.debug("Id evolucion pasado por parametro: " + idEvolucion);
        InformacionDatosFirmados infoDatosFirmados = null;
         
        try{
            ArrayList arrayListNumeroDocumentos = new ArrayList();
            
            try{ 
            	arrayListNumeroDocumentos = (ArrayList) request.getSession().getAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE);
            	//TODO Fernando Piedra. Se comprueba antes de continuar si alguna de las liquidaciones ya ha sido introducida en SUR.                
                ArrayList<String> documentosEnSur = new ArrayList<String>(); 
                boolean algunaPagada = false;
    	        for (int i = 0; i < arrayListNumeroDocumentos.size(); i++) {
    	        	boolean esPagada = false;
    	        	String idLiquidacion = (String) arrayListNumeroDocumentos.get(i);    	           
    		    	String modelo = idLiquidacion.substring(0, 3);
    		    	String version = idLiquidacion.substring(3,4);
    		    	String numeroDocumento = idLiquidacion.substring(4);
    		    	
    		    	//Obtenemos el liquidacion Info correspondiente y creamos el presentacion info para despues
                    liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
                    
    		    	//Antes de nada, obtengo el concepto del documento:
    		        String conceptoConsulta = null;
    		        if(liquidacionInfo != null){
    		            conceptoConsulta = Utiles.controlaConcepto(liquidacionInfo.getConcepto());
    		        }
    		    	
    	    		esPagada = getLiquidacionBLJBean(request).compruebaLiquidacionPagada(modelo,version,numeroDocumento, conceptoConsulta);
    	    		if(esPagada){
    	    			documentosEnSur.add(idLiquidacion);
    	    			algunaPagada = true;
    	    		}
                }
        		if(algunaPagada){    			
        			StringBuffer lista = new StringBuffer();
        			for (int i = 0; i < documentosEnSur.size(); i++){    				
        				String documento = (String)documentosEnSur.get(i);
        				lista.append(" " + documento + ";");
        			}
        			logger.error("De los documentos seleccionados, hay " + documentosEnSur.size() + " documento/s que ya se encuentra/n en SUR, y es/son: " + lista.toString());
        			ResultInfo resultError = new ResultInfo();
        			resultError.setError(true);
        			if(documentosEnSur.size() > 1){
        				resultError.setMessage("Alguno de los documentos que est� intentando pagar/presentar existen en el sistema. No es posible realizar su pago/presentaci�n telem�tico. Contacte con el servicio de atenci�n al usuario (CEIS) que podr� encontrar en la p�gina de contacto de nuestra Oficina Virtual.");
        			}else{
        				resultError.setMessage("El documento que est� intentando pagar/presentar existe en el sistema. No es posible realizar su pago/presentaci�n telem�tico. Contacte con el servicio de atenci�n al usuario (CEIS) que podr� encontrar en la p�gina de contacto de nuestra Oficina Virtual.");
        			}
        			request.setAttribute("resultInfo", resultError);
        			return getUrl(Constantes.MOSTRAR_ERROR);
        		}
            }catch (NullPointerException ex){
                ResultInfo resultInfo = new ResultInfo(true,"","Error: Se ha Firmado/Pagado/Presentado por otra ventana");
                request.setAttribute("resultInfo",resultInfo);
                request.setAttribute("presentacionInfoError", presentacionInfo);
                return getUrl(Constantes.MOSTRAR_ERROR);
            }
            
            boolean estaSeleccionadoNRCDif = (null != request.getParameter("esPagDif"))? Boolean.parseBoolean(request.getParameter("esPagDif")): false;
            if (estaSeleccionadoNRCDif){                
                logger.debug("Proceso Simplificado: Validamos el NRC del modo de pago NRC diferido antes de continuar con el proceso de firma/pago/presentaci�n");
                String idRecuperado = request.getParameter("id");
                String documentoRecueprado = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
                String sIdLiquidacionTemp = (String) arrayListNumeroDocumentos.get(0);
                LiquidacionInfo liquidacionInfoTemp = getLiquidacionBLJBean(request).getLiquidacion(userProfile, sIdLiquidacionTemp);                
                String url = validaNRCDiferido(request, userProfile, idRecuperado, documentoRecueprado, liquidacionInfoTemp);
                if (null != url && !"".equals(url)){
                    return url;
                }
            }
            
            logger.trace("##########################################################################################");
            logger.trace("##########################################################################################");
            logger.trace("####################### PROCESO DE PAGO PRESENTACION SIMPLIFICADO ########################");
            logger.trace("##########################################################################################");
            logger.trace("##########################################################################################");
            
                                                    
            /**********************/            
            /** PROCESO DE FIRMA **/
            /**********************/
            logger.trace("******************************");
            logger.trace("COMIENZO DEL PROCESO DE FIRMA ");
            logger.trace("******************************");
            
            for (int i=0; i<arrayListNumeroDocumentos.size();i++){
                String sIdLiquidacion = (String) arrayListNumeroDocumentos.get(i);                
                
                //Obtenemos el liquidacion Info correspondiente y creamos el presentacion info para despues
                liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, sIdLiquidacion);
                liquidacionInfo.setJustificante(liquidacionInfo.getIdLiquidacion());                                                            
                //TODO:Debo de recuperar de evoliq el estado 11, y recoger el numero de entidad y sucursal si existe
                presentacionInfo = new PresentacionInfo(liquidacionInfo); 
                presentacionInfo.setIdEvolucion(idEvolucion);
                
                if (liquidacionInfo.getIdEstadoActual()==Constantes.ESTADO_PENDIENTE_FIRMA){            
                    liquidacionInfo.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_FIRMA);           
                    presentacionInfo.setLiteralEstadoActual(getSessionJBean(request).getLiteral(Constantes.LITERAL_PENDIENTE_FIRMA));
                    
                    if (idEvolucion == getLiquidacionBLJBean(request).obtenerUltimoIdEvolucion(userProfile,liquidacionInfo)){
                    
                        //Evitar firmar si no hay permisos
                        //Comprobar que este activo el modelo de la autoliquidacion
                                
                        if (sId == null) {
                            errorEnProceso = new ResultInfo(true, "PDF003", getSessionJBean(request).getLiteral("PDF003"));
                            throw new Exception("No estaba el ID en el request ");
                        } else {        
                            //Si el parametro es -1 y no estamos en entorno de desarrollo.                  
                            if("-1".equals(sId)){
                                errorEnProceso = new ResultInfo(true, "PDF003", getSessionJBean(request).getLiteral("PDF003"));
                                logger.error("***ERROR EN EL PROCESO DE FIRMA*** El parametro identificador de firma recibido es: "+sId+" ,no se invocara al EJB de firma.");
                                throw new Exception("Parametro identificador de firma incorrecto = -1");                            
                            }else{
                                try {
                                    if (((HashMap<String,DatosAFirmar>)request.getSession().getAttribute(Constantes.LISTA_IDS)).get(sId) == null) {
                                        errorEnProceso = new ResultInfo(true, "PDF004", getSessionJBean(request).getLiteral("PDF004"));
                                        throw new Exception("No estaba el ID en la lista de IDs ");
                                    }
                                } catch (Exception e) {
                                    errorEnProceso = new ResultInfo(true, "PDF004", getSessionJBean(request).getLiteral("PDF004"));
                                    logger.error("inicioFirmaPagoTarjeta: Error chequeando si el ID est� en la lista de IDs ");
                                    throw e;
                                }
                                
                                try { 
                                    
                                    //Gestion si el documento se firma v�a cliente o v�a servidor
                                    firma = request.getParameter("firma");
                                    String aliasCert = userProfile.getAliasCert();
                                    ClienteFirma clienteFirma = null;
                                    double idTransaccionServidor = 0.0;
                                    byte[] datosFirmados = null;
                                    if("firmaServidor".equals(firma) && !Utilidades.estaVacia(aliasCert)){
                                        //Firmaremos v�a servidor
                                        clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA), aliasCert);
                                        datosFirmados = clienteFirma.getDocumento(Double.parseDouble(sId));
                                        idTransaccionServidor = clienteFirma.generarFirmaServidor(liquidacionInfo.getIdLiquidacion(), datosFirmados,"PDF", null, null);
                                        sId = String.valueOf(idTransaccionServidor);
                                       
                                    }else{
                                        clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA));
                                        //Recupero el objeto certificado de Session 
                                        CertificadoDigital cdAutenticado = (CertificadoDigital) request.getSession().getAttribute("__Certificado");
                                      //Recupero si el logado a la apliacion se hizo o no en plataforma 
                                        String loginExterno = (String) request.getSession().getAttribute(Constantes.LOGIN_EXT);
                                        //Obtenfo la informacion del Certificado utilizado en la firma
                                        CertificadoDigital cdFirmante = null;
                                        String certificadoFirmante = "";
                                        datosFirmados = clienteFirma.getDocumento(Double.parseDouble(sId));
                                        Vector<InformacionFirmante> firmantes = clienteFirma.verificarFirmaYExtraerFirmantes(Base64.decode(firma),datosFirmados);
                                        if(!firmantes.isEmpty()){
                                            InformacionFirmante firmante = firmantes.get(0);
                                            cdFirmante = firmante.getCertificadoDigital();
                                            certificadoFirmante = firmante.getCertificado();
                                        }
                                        
                                        logger.debug("APLICACION: " + liquidacionInfo.getNombreAplicacion());
                                        //Si es PLAGES no controlo esto pq no tenemos certificado auternticado, lo hizo en PLAGES
                                        boolean certificadoValido = false;
                                        if ("true".equals(loginExterno)){
                                            //Si el login se hizo desde aplicacion externa se valida al menos que el nif del certificado
                                            //coincida con el usuario identificado
                                            if(cdFirmante.getNif().equals(userProfile.getNif())){
                                                certificadoValido = true;
                                            }

                                        } else if ("GESTORIA".equals(liquidacionInfo.getNombreAplicacion())){
                                            //PLAGES
                                            //Se realiza el comit de la firma.
                                            certificadoValido = true;

                                        } else {
                                            logger.debug("cdAutenticado:"+cdAutenticado);
                                            logger.debug("cdFirmante:"+cdFirmante);
                                            if(cdAutenticado.equals(cdFirmante) || ControladorSimulador.esSimuladorActivoUsuario()){
                                                certificadoValido = true;
                                            }
                                        }
                                        if (certificadoValido){
                                            //Se realiza el comit de la firma.
                                            firma = request.getParameter("firma");                                         
                                            infoDatosFirmados = clienteFirma.generarFirma(Double.parseDouble(sId), firma, certificadoFirmante, sIdLiquidacion, null);
                                        }else {
                                            errorEnProceso = new ResultInfo(true, "PDF011", getSessionJBean(request).getLiteral("PDF011"));
                                            throw new Exception("El certificado utilizado en la firma no coincide con el utilizado en la autenticacion de la aplicaci�n.");
                                        }
                                    }
                                    
                                } catch (FirmaException e) {
                                    errorEnProceso = new ResultInfo(true, "PDF007", getSessionJBean(request).getLiteral("PDF007"));
                                    throw  e;
                                }
                            }
                            // Cambio el estado de la autoliquidacion
                            liquidacionInfo.setIdEstadoActual(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                            liquidacionInfo.setIdDocumentoFirma(Double.parseDouble(sId));
                            //&& idTransaccionServidor909 != null
                            if (liquidacionInfo.getLiquidacionInfo909() != null ) {
                                //Id firma para carta de pago 909
                                liquidacionInfo.getLiquidacionInfo909().setIdEstadoActual(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                                evolucionLiquidacionInfo909 = new EvolucionLiquidacionInfo();
                                evolucionLiquidacionInfo909.setIdTipoEstado(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                                evolucionLiquidacionInfo909.setIdLiquidacion(liquidacionInfo.getLiquidacionInfo909().getIdLiquidacion());
                                evolucionLiquidacionInfo909.setIdUsuario(userProfile.getIdUsuario());
                                evolucionLiquidacionInfo909.setFechaEstado(new java.util.Date());
                                evolInfo909=getPresentacionBLJBean(request).setPresentacion(userProfile, liquidacionInfo.getLiquidacionInfo909(), evolucionLiquidacionInfo909);
                            } 
                            //vuelvo a actualizar la presentacion con los ultimos cambios
                            presentacionInfo = new PresentacionInfo(liquidacionInfo);
                            evolucionLiquidacionInfo = new EvolucionLiquidacionInfo();
                            evolucionLiquidacionInfo.setIdTipoEstado(Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR);
                            evolucionLiquidacionInfo.setIdLiquidacion(liquidacionInfo.getIdLiquidacion());
                            evolucionLiquidacionInfo.setIdUsuario(userProfile.getIdUsuario());
                            evolucionLiquidacionInfo.setFechaEstado(new java.util.Date());
                            evolInfo=getPresentacionBLJBean(request).setPresentacion(userProfile, liquidacionInfo, evolucionLiquidacionInfo);
                            
                            // Si venimos desde GESTORIA como Empleado P�blico actualizamos tambi�n el estado en la plataforma de gestoria 
                            if (null != liquidacionInfo.getNombreAplicacion() && liquidacionInfo.getNombreAplicacion().equals("GESTORIA")){
                            	logger.debug ("Actualizamos el documento en la plataforma de Gestor�a");   
                            	try{                            		
                            		getLiquidPreseBLJBean(request).actualizarGestoria(userProfile, presentacionInfo.getIdLiquidacion(), presentacionInfo.getIdEstadoActual(), "");
                            	}catch (Exception ex){
                                	logger.error("Error al actualizar el estado del documento " + presentacionInfo.getIdLiquidacion() + " en la plataforma de gestor�as. Excepcion: " + ex);            		
                            	}
                            }                            
                            presentacionInfo.setIdEvolucion(evolInfo.getIdEvolucion());
                        } //end else
                        //El documento ya ha sido firmado ==> lo elimino de sesi�n
                        ((HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS)).remove(sId);

                        if (null != request.getSession().getAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE)){
                        	request.getSession().removeAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE); 
                        }                                       
                    }else{
	                    ResultInfo resultFirma = new ResultInfo(true,"","Error: Se est� firmando esta liquidaci�n por otra ventana");
	                    request.setAttribute("resultInfo",resultFirma);
	                    request.setAttribute("presentacionInfoError", presentacionInfo);
	                    return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                }
            }            
        
            //FIN PROCESO FIRMA         
            logger.trace("******************************");
            logger.trace("  FIN DEL PROCESO DE FIRMA ");
            logger.trace("******************************");
            
            //Parche para corregir el error durante la presentaci�n de liquidaciones por empleados publicos
            //con importe 0 en los casos que ya hubiera expedido el correspondiente AU7
            if(liquidacionInfo.getTotalIngresar()==0L && "INGRESOSUR".equals(liquidacionInfo.getNombreAplicacion())) {
            	pagoTarjeta = "";
            }
            
            //Se hace distincion sobre si es pago con tarjeta o no:
            if(pagoTarjeta != null && pagoTarjeta.equals("pagoTarjeta")){
                //PAGO CON TARJETA
                int bloqueado = 1;
                if (presentacionInfo != null && presentacionInfo.getTotalIngresar() > 0){
                    //Bloquear documento y Actualizar la evolucion del documento, insertando dos estado un Intento Pago y un Pendiente Pagar/Presentar 
                    bloqueado = bloqueoActEvolucion(request,presentacionInfo, Constantes.GESTION_PRES_ERROR, "FUNCIONARIO");
                    if (bloqueado != 0){
                        ResultInfo resultInfo = new ResultInfo(true,Constantes.GESTION_PRES_ERROR,getSessionJBean(request).getLiteral(Constantes.GESTION_PRES_ERROR));
                        throw new ApplicationError(resultInfo, new Exception("Error al bloquear el documento "+presentacionInfo.getIdLiquidacion()+" en el inicio pago con tarjeta."));
        
                    }
                    
                    if(presentacionInfo.getTotalIngresar() != 0){
                    	DecimalFormat df = new DecimalFormat("#.##");
                    	
                    	logger.debug("Importe a pagar (sin mascara): " + presentacionInfo.getTotalIngresar());
                        String importe = df.format(presentacionInfo.getTotalIngresar() * 100);
                        logger.debug("Importe a pagar (con mascara): " + importe);
                        
                        request.setAttribute("importeTotal",importe);
                        request.setAttribute("idLiquidacion",presentacionInfo.getIdLiquidacion());	 
                        request.setAttribute("pagoTarjeta",pagoTarjeta);
                        //Pago con tarjeta Empleado P�blico
                        urlDst = getUrl(Constantes.PAGO_TPV_PINPAD);
                    }else{
                    	logger.error("datosFuncionario: El importe es nulo");
                        throw new Exception("El importe es nulo, por lo que no podemos pagar nada....");
                    }
                    
                    return urlDst;
                    
                }else{
                	logger.error("No existe ninguna liquidacion para pagar con tarjeta, presentacionInfo"+presentacionInfo);
                    logger.error("firmaPagoPresentacion: El importe de la liquidacion debe de ser mayor que 0");
                    return getUrl(Constantes.MOSTRAR_ERROR);
                }
                
            }else{
            /*****************************************/
            /** RECOGEMOS LOS PARAMETROS NECESARIOS **/
            /*****************************************/ 
            
            //Datos entidad financiera
            String nombreEntidadFinanciera = "";
            String codigoIban = "";
            int codEntidad = 0;
            String CCC = "";
            String codigoEntidadObject = "";
            String nrcDiferido = null;
            String fechaPagoDiferido = null;
            boolean existen_datos = false;
            boolean esPagoConcuenta = false; 
            boolean esPagoDiferido = false;
            if (presentacionInfo.getTotalIngresar() > 0){                
            	String idCuenta = request.getParameter("idCuenta");
            	if (userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_PROFESIONAL && null != idCuenta && !"".equals(idCuenta)
            		&& !presentacionInfo.isPagoDiferido()){
            		CuentaBancariaAutorizacionInfo cuentaBancaria = getContratoBLJBean(request).getCuentaBancariaAsociadasContrato(userProfile, idCuenta);
            		codigoEntidadObject = cuentaBancaria.getCodigoEntFin();
            		nombreEntidadFinanciera = cuentaBancaria.getNombreEntidad();            		
            		codEntidad = Integer.parseInt(cuentaBancaria.getCodigoEntFin());     
            		codigoIban = request.getParameter("codigoIban");
            		String codigoIbanAlmacenado = cuentaBancaria.getIban();
            		logger.debug("CodigoIban parametro:"+codigoIban+" CodigoIban almacenado:"+codigoIbanAlmacenado);
            		codigoIban = codigoIbanAlmacenado;
            		/*if(codigoIbanAlmacenado != null 
            				&& !codigoIbanAlmacenado.equals(codigoIban)){
            			logger.error("Error al validar el IBAN de la cuenta bancaria asociada al contrato profesional: " + codigoIbanAlmacenado + ". IBAN de la p�gina: " + codigoIban);
            			request.setAttribute("resultInfo", new ResultInfo(true,"","El c�digo IBAN es incorrecto. Por favor, contacte con el servicio de atenci�n al usuario de la Consejer�a de Hacienda y Adm. P�blica para intentar subsanar el problema."));
            			urlDst = getUrl(Constantes.MOSTRAR_ERROR);
            			return urlDst;
            		}*/
            		existen_datos = true;
            	}else{
            		if(userProfile.getIdTipoContrato() == Constantes.ID_TIPO_FUNCIONARIO) {
						//Modalidad de presentaci�n para los EP mediante NRC emitido en Entidad Bancaria o NRC Ficticio
						existen_datos = false;
						String entidadFinancieraAux = request.getParameter("entidadFinanciera");
						codigoEntidadObject = new String(entidadFinancieraAux);
						nrcDiferido = userProfile.getAutorizacion().getNrc();
						userProfile.setCodigoIban(null);
					}else {
	            	    // Para saber si es Pago diferido pero lo estoy pagando con cuenta                           
	                    estaSeleccionadoNRCDif = (null != request.getParameter("esPagDif"))? Boolean.parseBoolean(request.getParameter("esPagDif")): false;
	                    String entidadFinancieraAux = null;
	                    if (estaSeleccionadoNRCDif){
	                        entidadFinancieraAux=request.getParameter("entidadFinancieraDif");
	                    }else{
	                        entidadFinancieraAux=request.getParameter("entidadFinanciera");
	                    }
		            	if (null != entidadFinancieraAux){ 
		                    //Datos entidad financieran
		                    codigoEntidadObject = new String(entidadFinancieraAux);
		                    // Para saber si es Pago diferido pero lo estoy pagando con cuenta	                          
		                    estaSeleccionadoNRCDif = (null != request.getParameter("esPagDif"))? Boolean.parseBoolean(request.getParameter("esPagDif")): false;
		                    presentacionInfo.setPagoCuentaOTarjeta(!estaSeleccionadoNRCDif);
		                    if (!presentacionInfo.isPagoDiferido() 
		                            || (presentacionInfo.isPagoDiferido() && !estaSeleccionadoNRCDif)
		                            || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
		                    	codigoIban = request.getParameter("codigoIban");
		                    	logger.debug("codigoIban:"+codigoIban);
		                    }else{
		                    	nrcDiferido= request.getParameter("nrcDiferido");
		                    	fechaPagoDiferido = request.getParameter("fechaPagoDiferido");	
		                    }
		                    if(userProfile.getIdTipoContrato()==Constantes.ID_TIPO_CONTRATO_FUNCIONARIO
		                    		|| userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO_R40){
		                        nombreEntidadFinanciera = (String) request.getSession().getAttribute("nombreEntidadFinanciera");
		                    }else{
		                        nombreEntidadFinanciera = request.getParameter("nombreEntidadFinanciera");
		                    }
		                    if (!presentacionInfo.isPagoDiferido() 
	                                || (presentacionInfo.isPagoDiferido() && !estaSeleccionadoNRCDif)
	                                || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
		                        codEntidad = Integer.parseInt(request.getParameter("entidadFinanciera"));
		                    }else{
		                        codEntidad = Integer.parseInt(request.getParameter("entidadFinancieraDif"));
		                    }
		                    existen_datos = true;   
		            	}	            	
					}
	            	if (existen_datos){
		                //Guardamos los datos en el userProfile 
		                userProfile.setCodEntidadObject(new Integer(codigoEntidadObject));
		                // Para saber si es Pago diferido pero lo estoy pagando con cuenta
		                esPagoConcuenta = !StringUtils.isEmpty(codigoIban);
		                if (esTarjeta != null &&!esTarjeta.equals("true")){
		                    if (!presentacionInfo.isPagoDiferido() 
		                		        || (presentacionInfo.isPagoDiferido() && esPagoConcuenta)
		                                || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
		                        userProfile.setCodigoIban(codigoIban);
		                        
		                    }else if (estaSeleccionadoNRCDif){
		                        userProfile.setCodigoIban(null);
		                    }
		                                                                                
		                    StringBuffer cccStringBuffer = new StringBuffer(Text.changeSize(codigoEntidadObject,4,'0',false));
		                    if (!liquidacionInfo.isPagoDiferido() 
		                            || (liquidacionInfo.isPagoDiferido() && esPagoConcuenta)
		                            || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){	                        
	//    	                    cccStringBuffer.append("/");
	//                        	cccStringBuffer.append(Text.changeSize(codigoSucursal,4,'0',false));
	//                        	cccStringBuffer.append("/");
	//                        	cccStringBuffer.append(Text.changeSize(digitoControl,2,'0',false));
	//                        	cccStringBuffer.append("/");
	//                        	cccStringBuffer.append(Text.changeSize(numeroCuenta,10,'0',false));
		                        if(userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
		                            codigoIban = userProfile.getAutorizacion().getCodigoIban();
		                        }
		                        cccStringBuffer = new StringBuffer(Utiles.formateaIban(codigoIban));
		                        
		                    }
		                        
		                    CCC = cccStringBuffer.toString();
		                }	                
	            	}
            	}
                //Info log
                logger.trace("**********************************************************************************************");
                logger.trace("Recogiendo los parametros necesarios para iniciar el proceso de pago/presentacion SIMPLIFICADO");
                logger.trace("idFirma: " +sId);
                logger.trace("Codigo Territorial: " +codigoTerritorial);
                logger.trace("Nombre Entidad Financiera: " + nombreEntidadFinanciera);
                logger.trace("Codigo entidad financiera: " + codEntidad);
                logger.trace("CCC: " +CCC);
                //logger.trace("N� Tarjeta: " +tarjeta);
                logger.trace("**********************************************************************************************");
            }
            /**************************/ 
            /**** PROCESO DE PAGO *****/    
            /**************************/
            logger.trace("*************************");
            logger.trace("COMIENZO DEL PROCESO PAGO");
            logger.trace("*************************");
            
                if (presentacionInfo.getTotalIngresar() > 0){
                    presentacionInfo.setCodEntidadObject(new Integer(codigoEntidadObject));
                    if (!presentacionInfo.isPagoDiferido() 
                            || (presentacionInfo.isPagoDiferido() && esPagoConcuenta)
                            || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
                    	if (codigoIban != null && !codigoIban.isEmpty()){
                    	    logger.info("Guardamos el codigoIban en presentacionInfo");
                        	presentacionInfo.setCodigoIban(codigoIban);
                    	}else if (nrcDiferido!=null && !nrcDiferido.isEmpty()) {
							presentacionInfo.setNrc(nrcDiferido);
							SimpleDateFormat simpleDate = new SimpleDateFormat(Constantes.FORMATO_FECHA_CONSULTA);
							presentacionInfo.setFechaPago(userProfile.getAutorizacion().getFechaPago());
							presentacionInfo.setCodigoIban(null);
							//INSERTAMOS EN SN_WEBLIQ el INDPAGDF
			                boolean resultado = getLiquidacionBLJBean(request).setIndPagoNRCDiferidoInterno(userProfile, presentacionInfo.getIdLiquidacion(),"1");
			                //Obtenemos indnrcdf despues de insertarlo
			                if(resultado && presentacionInfo != null && presentacionInfo.getIdLiquidacion() != null){
			                    String pagoDiferidoAct = getLiquidacionBLJBean(request).getIndPagoNRCDiferido(userProfile, presentacionInfo.getIdLiquidacion(), Long.parseLong(presentacionInfo.getIdLiquidacion().substring(0, 4)));
			                    if("1".equals(pagoDiferidoAct)){
			                    	presentacionInfo.setPagoDiferido(true);
			                        esPagoDiferido = presentacionInfo.isPagoDiferido();
			                    }
			                }else{
			                    logger.error("No existe numero de documento seleccionado o no se ha insertado correctamente el indicador de pago NRCDIF en webliq");
			                    ResultInfo resultInfo = new ResultInfo(true,null,"El documento seleccionado no existe en el sistema");
			                    request.setAttribute("resultInfo",resultInfo);
			                    return getUrl(Constantes.MOSTRAR_ERROR);
			                }
                        }
                    }else{
                    	presentacionInfo.setNrc(nrcDiferido);
                    	SimpleDateFormat simpleDate = new SimpleDateFormat(Constantes.FORMATO_FECHA_CONSULTA);								 			
						presentacionInfo.setFechaPago(simpleDate.parse(fechaPagoDiferido));
						presentacionInfo.setCodigoIban(null);
                    }
                    presentacionInfo.setFechaCadTarjetaObject(null);
                    presentacionInfo.setTarjetaObject(null);

                }
                presentacionInfo.setCodigoTerritorial(codigoTerritorial);
                logger.debug("PresentacionInfo antes de pagarPresentar:"+presentacionInfo.toString());
                presentacionInfo = pagarPresentar(request, userProfile, presentacionInfo);
                if (presentacionInfo.getResultInfo().getSubCode().equals(Constantes.GESTION_PAGO_ERROR)){
                    request.getSession().setAttribute("resultInfo", presentacionInfo.getResultInfo());
                    request.setAttribute("presentacionInfoError", presentacionInfo);
                    return getUrl(Constantes.MOSTRAR_ERROR);
                }
                if (presentacionInfo.getResultInfo().getError()){
                    request.getSession().setAttribute("errorEnProceso", presentacionInfo.getResultInfo());
                } 
                request.getSession().setAttribute("presentacionInfo", presentacionInfo);
                
                logger.trace("##########################################################################################");
                logger.trace("##########################################################################################");
                logger.trace("################### FIN PROCESO DE PAGO PRESENTACION SIMPLIFICADO ########################");
                logger.trace("##########################################################################################");
                logger.trace("##########################################################################################");
    
                request.setAttribute("nombreEntidadFinanciera", nombreEntidadFinanciera);
                request.setAttribute("ccc", CCC);
                
                // Si hay URL de grabaci�n, reenviar los datos a dicha URL
                boolean hayUrlRecibo = devolverUrlRecibo(request, userProfile, liquidacionInfo);
                boolean estadoFinal = false;
                if(presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO 
                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_NRC)
                estadoFinal = true;     
                
                String aplicacion = getPresentacionBLJBean(request).obtenerNombreAplicacion(liquidacionInfo.getIdLiquidacion(), userProfile, estadoFinal);
                request.getSession().setAttribute("aplicacion", aplicacion);
                boolean grabado = false;
                
                // Si la peticion es externa
                if(aplicacion != null && !aplicacion.equals("")){
                	if (null != liquidacionInfo.getNombreAplicacion() && liquidacionInfo.getNombreAplicacion().equals("GESTORIA")){
                    	logger.debug ("Actualizamos el documento en la plataforma de Gestor�a");   
                    	try{
                    		String codErrorEnPago = (null != ((String)request.getAttribute("motivoError"))) ? (String)request.getAttribute("motivoError") : ""; 
                    		getLiquidPreseBLJBean(request).actualizarGestoria(userProfile, presentacionInfo.getIdLiquidacion(), presentacionInfo.getIdEstadoActual(), codErrorEnPago);
                    	}catch (Exception ex){
                        	logger.error("Error al actualizar el estado del documento " + presentacionInfo.getIdLiquidacion() + " en la plataforma de gestor�as. Excepcion: " + ex);            		
                    	}
                    }
                	
                	String urlGrabacion = getPresentacionBLJBean(request).obtenerURLGrabacion(aplicacion, userProfile);
                    if (urlGrabacion != null && !"".equals(urlGrabacion)) {
                        //Hacer la peticion de envio a dicha url
                        CifradoInfo datosCifrado = getPresentacionBLJBean(request).obtenerDatosCifrado(aplicacion, userProfile);
                        enviarUrlGrabacion(datosCifrado, urlGrabacion, presentacionInfo);
                        //Envio de la confirmacion de grabacion
                        presentacionInfo.setEntrega(1);
                        try{
                            boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
                            if(!result){
                                throw new Exception("Error en la actualizacion de idEntrega");
                            }else{
                                grabado = result;
                            }
                        }catch(Exception e){
                            logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
                            throw new Exception(e);
                        }
                    }
                }
                
                if (presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO ||
                        presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR ||
                        presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO) {
                    request.getSession().setAttribute("errorEnProceso",new ResultInfo(false,"",""));
                } else {
                    request.getSession().setAttribute("errorEnProceso",new ResultInfo(true,"",""));
                }
                
                //REDIRECCIONAMOS A LA PAGINA CON EL RESULTADO DEL PROCESO
                urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_FRAME_RESULTADO_SERVLET);
                
                // Si es una presentaci�n simplificada volveremos a la aplicaci�n que llama, si no, nos quedamos en la plataforma de pago
                String accesoSimp = (String)request.getSession().getAttribute("simplificado");
                logger.debug("Acceso Simplificado para URL de recibo: " +accesoSimp + " , URLRecibo?: " + hayUrlRecibo);
                
                if (hayUrlRecibo && ((null != accesoSimp && "true".equals(accesoSimp)) ||
                	(null != request.getSession().getAttribute("accesoPlages") && "true".equals(request.getSession().getAttribute("accesoPlages"))))) {
                	
                	if (null != presentacionInfo.getNombreAplicacion() && presentacionInfo.getNombreAplicacion().equals("GESTORIA")) {
                		//Como hay urlRecibo, redirecciono
						if (request.getAttribute("urlRecibo") != null) {
							logger.debug("Redirecciono a la url: " + (String)request.getAttribute("urlRecibo"));
							request.setAttribute("aplicacion","GESTORIA");
							urlDst = getUrl(Constantes.URL_RECIBO_TARJETA_FRAME);
						} else {
							throw new Exception("No existe url de recibo");
						}
                	} else {
	                    boolean estadoFinalRecibo = false;
	                    if (presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO 
	                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
	                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
	                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_NRC) {
	                        estadoFinalRecibo = true;
	                    }
	                    
	                    String aplicacionRecibo = getPresentacionBLJBean(request).obtenerNombreAplicacion(presentacionInfo.getIdLiquidacion(), userProfile, estadoFinalRecibo);
	                    //Creacion del String encriptado a partir del DocRespuesta que viene del jar de tasas
	                    DocumentoRespuesta docRes = enviarUrlRecibo(request,true,presentacionInfo,presentacionInfo.getIdEstadoActual(),presentacionInfo.getReferencia());
	                    String xmlEncriptado = obtenerDocResEncriptado(request,docRes,aplicacionRecibo);
	                    if (xmlEncriptado != null) {
	                        request.setAttribute("datosEncriptados", xmlEncriptado);
	                        urlDst = getUrl(Constantes.URL_RECIBO);
	                    } else {
	                        logger.error("Ha habido un error al intentar encriptar el docRespuesta que debemos de enviar para la aplicacion: " + aplicacionRecibo);
	                        urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	                    } 
	                    
	                    //Envio de la confirmacion de recibo
	                    if (grabado) {
	                        presentacionInfo.setEntrega(2);
	                    } else {
	                        presentacionInfo.setEntrega(3);
	                    } 
	                    
	                    try {
	                        boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
	                        if(!result){
	                            throw new Exception("Error en la actualizacion de idEntrega");
	                        }
	                    } catch(Exception e) {
	                        logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
	                        throw new Exception(e);
	                    }
                	}
               }
    
            } //Fin del if de tarjeta/CCC
            
        } catch (Exception e) {
        	if(e != null && e.getMessage() != null && e.getMessage().equals("Error al realizar la consulta al Santander")){
                logger.error("En estos momentos el banco Santander no est� operativo. Int�ntelo de nuevo m�s tarde.");
                request.setAttribute("resultInfo", new ResultInfo(true,"","En estos momentos el banco Santander no est� operativo. Int�ntelo de nuevo m�s tarde."));
                try{
                    desbloquear(request, presentacionInfo.getIdLiquidacion());
                }catch(Exception ex){
                    request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                }
                urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                return urlDst;
            }else{
                logger.error("Error en el proceso de pago y presentacion simplificada: " +JFactoryException.getStackTrace(e));                      
                if (errorEnProceso == null)
                    errorEnProceso = new ResultInfo(true, "PDF005", getSessionJBean(request).getLiteral("PDF005"));
                logger.trace("Guardamos en el request la informacion del error: " +errorEnProceso);             
                request.getSession().setAttribute("presentacionInfo", presentacionInfo);
                request.setAttribute("presentacionInfoError", presentacionInfo);
                request.getSession().setAttribute("errorEnProceso", errorEnProceso);   
                if (null != liquidacionInfo.getNombreAplicacion() && liquidacionInfo.getNombreAplicacion().equals("GESTORIA") && 
                		(userProfile.getIdTipoContrato() == Constantes.ID_TIPO_FUNCIONARIO || userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO_R40))
                	urlDst = getUrl(Constantes.MOSTRAR_ERROR);
            }   
        }
        logger.debug("urlDst: " +urlDst);
        return urlDst;
    }
    
/**
     * Metodo que recoge el resultado de la operacion contra TPV IECISA de funcionario y realiza la presentacion del 
     * documento, y/o muestra lo que haya ocurrido en ese pago.
     * @author VAS000 29/03/2007
     * @param request
     * @param userProfile
     * @return urlDst
     * @throws SAXException 
     * */    
    private String presentacionTPVPinPadFuncionario(HttpServletRequest request, UserProfileInfo userProfile) {    
        
    	LiquidacionInfo liquidacionInfo = null;
        String fechaOperacionP = "";
        String horaOperacionP = "";
        String codAutoriz ="";
        String idTransaccion = "";
        String centroVenta = "";
        String tpvVenta = "";
        String idComercio = "";
        String codigoError ="";
        String errorDesc = "";
        long codUsuario, idEvolucion = 0;
        String literalEstadoActual="";
        //Recojo la fecha de hoy
        java.util.Date fechaOperacionPago = null;
        //Recojo la fecha de hoy
        java.util.Date fechaPresentacion = new java.util.Date();
        String pagoTarjeta = request.getParameter("pagoTarjeta");
        String importeTotal = request.getParameter("importeTotal");
        String idLiquidacion = request.getParameter("idLiquidacion");
        String codError = request.getParameter("codError");
       
        String descrpErrorXml = request.getParameter("descrpError");
        
        liquidacionInfo = getPresentacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
        //Se borra el literal de Banco recogido
        liquidacionInfo.setLiteralEntidad(null);
        PresentacionInfo presentacionInfo = new PresentacionInfo(liquidacionInfo);
    
        logger.debug("presentacionTPVIECISAFuncionario: PARAMETROS: " + pagoTarjeta +" ,"+ importeTotal +" ,"+ idLiquidacion +" ,"+ codError +" ,"+ descrpErrorXml);
        
        try{
        	fechaOperacionP = (String)request.getParameter("fechaPago");
        	horaOperacionP = (String)request.getParameter("horaPago");
        	logger.debug("Recojo la fecha y la hora del pago proveniente del TPV: Fecha (AAAAMMDD): " + fechaOperacionP + " , hora (HHMMSS): " + horaOperacionP);
        	codAutoriz = (String)request.getParameter("codigoAutorizacion");
            idTransaccion = (String)request.getParameter("codigoTransaccion");
        	idComercio = (String)request.getParameter("idComercio");
        	centroVenta = (String)request.getParameter("centroVenta");
        	tpvVenta = (String)request.getParameter("tpvVenta");
        	
            //Tratamiento de lo que ha pasado en el Applet
            if(codError != null && codError.equals("000") && !"".equals(codAutoriz) && !"".equals(fechaOperacionP)){
                try{
                    //Todo ha ido correcto prosigo con la presentacion del documento.
                    presentacionInfo.setJustificante(presentacionInfo.getIdLiquidacion());
                    //Preparamos el presentacionInfo para presentarlo
                    logger.debug("Traspaso fecha y hora a Date");
                    SimpleDateFormat fechaConFormato = new SimpleDateFormat("yyyyMMdd HHmmss");
                    
                    try{
                    	fechaOperacionPago = fechaConFormato.parse(fechaOperacionP + " " + horaOperacionP);
                	}catch(Exception e){
                		logger.error("Error al parsear la fecha y la hora del pago");
                		throw new Exception("Error al parsear la fecha y la hora: " + e.getMessage());
                	}
                	logger.debug("presentacionTPVIECISAFuncionario: PARAMETROS RECOGIDOS PAGO TARJETA PINPAD CORRECTO: " + fechaOperacionPago + ", "+ codAutoriz + " ," + idTransaccion);
                	
                    //Preparamos el presentacionInfo para presentarlo
                    presentacionInfo.setFechaPago(fechaOperacionPago);
                    presentacionInfo.setFechaPagoTarjeta(fechaOperacionPago);
                    //MARCA FECHA PRESENTACION
                    presentacionInfo.setFechaPresentacion(fechaPresentacion);
                    presentacionInfo.setCodigoAutorizacionTarjeta(codAutoriz);
                    presentacionInfo.setIdTransaccionPassat(idTransaccion);
                    presentacionInfo.setIdTPVPago(tpvVenta);
                    presentacionInfo.setLiteralEntidad("");
                    presentacionInfo.setCentroVenta(centroVenta);
                	presentacionInfo.setTpvVenta(tpvVenta);
                    //Obtengo el id de evolucion ultimo referente a esa liquidacion
                    long idEvolucionTar = getLiquidacionBLJBean(request).obtenerUltimoIdEvolucion(userProfile,liquidacionInfo);
                    presentacionInfo.setIdEvolucion(idEvolucionTar);
                    
                    try{
                        presentacionInfo = getLiquidacionBLJBean(request).inicioConsultaTarjeta(userProfile,presentacionInfo);
                    }catch(Exception e){
                        logger.error("presentacionTPVIECISAFuncionario: Excepcion iniciando la consulta de tarjeta: " + e.toString());
                        throw new Exception("No debo de desbloquear el documento");
                    }
                    
                    if (userProfile == null){
                        //Recupero el usuario ya grabado en BD
                        codUsuario = liquidacionInfo.getIdUsuario();
                        userProfile = getContratoBLJBean(request).getUserProfile(String.valueOf(codUsuario));
                    }
                    //Obtengo el id de evolucion ultimo referente a esa liquidacion
                    idEvolucion = getLiquidacionBLJBean(request).obtenerUltimoIdEvolucion(userProfile,liquidacionInfo);
                    presentacionInfo.setIdEvolucion(idEvolucion);
                    //Cargo el literal
                    TipoEstadoLiquidacionInfo estadoLiquidacionInfo = getPresentacionBLJBean(request).getTipoEstadoLiquidacion(userProfile, presentacionInfo.getIdEstadoActual());
                    literalEstadoActual = getSessionJBean(request).getLiteral(estadoLiquidacionInfo.getLiteral());
                    presentacionInfo.setLiteralEstadoActual(literalEstadoActual);
                    //Se actualiza en base de datos sn_evoliq y sn_webliq
                    ResultInfo resltInfo = completarDatosEvolucion(request, userProfile, presentacionInfo);
                    if(resltInfo.getError()){
                        throw new Exception(resltInfo.getMessage());
                    }
                    ResultInfo resultInfo = getCombosOficinaPresentacion(request,userProfile,presentacionInfo);
                    String codTerriOfic="";
                    if(resultInfo != null && !resultInfo.getError()){
                        //Recojo el codigo Territorial
                        if(request.getSession().getAttribute("codigoTerritorialOficinaPresentacion") != null){
                            codTerriOfic = (String)request.getSession().getAttribute("codigoTerritorialOficinaPresentacion");
                            if(codTerriOfic != null && !codTerriOfic.equals("")){
                                liquidacionInfo.setCodigoTerritorial(codTerriOfic);
                                presentacionInfo.setCodigoTerritorial(codTerriOfic);
                            }else{
                                logger.debug("presentacionTPVIECISAFuncionario: El c�digo Territorial recogido es nulo o vacio para el codTerriDoc: " + presentacionInfo.getCodTerriDoc());
                            }   
                        }   
                    }   
                    
//                  Miramos que estado tenemos en tarjeta
                    /*if(presentacionInfo.getIdEstadoTarjeta() != Constantes.ESTADO_ERROR_TECNICO){
                        logger.debug("consultaPresentacionTarjeta: El documento se queda en Error Tecnico en Presentacion");
                        getPresentacionBLJBean(request).sendMailAdmin(userProfile,presentacionInfo,0,Constantes.MENSAJE_ERROR_EN_PRESENTACION);
                        
                    }else{*/
                    
                    /*** POR SI ACASO ANTES DE PRESENTAR COMPROBAMOS EL IMPORTE DE LA AUTOLIQUIDACION Y SI TIENE IDTRANSACCION ***/
                    logger.trace("presentacionTPVIECISAFuncionario: ###FINALIZADA LA LOGICA DEL PAGO A TRAVES DEL TPV PINPAD SE PROCEDE A LA LOGICA DE LA PRESENTACION###");
                    logger.trace("presentacionTPVIECISAFuncionario: Info de la autoliquidacion: " +presentacionInfo.getIdLiquidacion()+ " - IMPORTE: " +presentacionInfo.getTotalIngresar()+" CODAUTOR: " +presentacionInfo.getCodigoAutorizacionTarjeta());
                    boolean tieneQuePagarse = (presentacionInfo.getTotalIngresar() > 0.0);
                    logger.trace("presentacionTPVIECISAFuncionario: �TIENE QUE HABERSE PAGADO PREVIAMENTE? ->" +tieneQuePagarse);
                    boolean estaPagada = ((presentacionInfo.getCodigoAutorizacionTarjeta() != null) && (!"".equals(presentacionInfo.getCodigoAutorizacionTarjeta())));              
                    logger.trace("presentacionTPVIECISAFuncionario: �ESTA PAGADA? ->" +estaPagada);
                    if ( ((tieneQuePagarse) && (estaPagada)) ){
                        logger.trace("presentacionTPVIECISAFuncionario: TODO OK, PRESENTAMOS.");    
                        request.setAttribute("pagoTarjeta","true");
                        presentacionInfo = presentarSUR(request, userProfile, presentacionInfo);
                        //Introduzco el estado en el que se ha quedado la liquidacion para saber despues como esta
                        request.getSession().setAttribute("estadoLiquidacion",Long.valueOf(presentacionInfo.getIdEstadoActual()));
                        if (presentacionInfo.getResultInfo().getError() && (presentacionInfo.getResultInfo().getSubCode()).equals("Gesti�n Presentaci�n")){
                            request.setAttribute("resultInfo",presentacionInfo.getResultInfo());
                            logger.debug("presentacionTPVIECISAFuncionario: Error al presentar en SUR, despues de la logica de pago con tarjeta TPV PINPAD");
                        }
                    }else {
                        presentacionInfo.setMotivoLiquidacion(getSessionJBean(request).getLiteral(Constantes.MOTIVO_ERROR_CONEXION));
                        presentacionInfo.setIdEstadoActual(Constantes.ESTADO_ERROR_EN_PAGO);
                    }
                    
                    //}
                    //Desbloqueamos el documento
                    try {
                        desbloquear(request, liquidacionInfo.getIdLiquidacion());
                    } catch (Exception e){
                        logger.error ("presentacionTPVIECISAFuncionario: Error al tratar de desbloquear el documento en el proceso del pago con tarjeta con TPV PINPAD. IdLiquidacion: "+liquidacionInfo.getIdLiquidacion());
                        request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                        return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                    //Hemos desbloqueado y realizado todas las insercciones en Base de Datos devolvemos a una jsp de resultado de funcionario
                }catch(Exception e){
                    logger.error("presentacionTPVIECISAFuncionario: Error al presentar la liquidacion" + presentacionInfo.getIdLiquidacion() + " tras el pago con tarjeta TPV PINPAD correcto."+e.getMessage(), e);
                    try {
                        desbloquear(request, liquidacionInfo.getIdLiquidacion());
                    } catch (Exception ex){
                        logger.error ("presentacionTPVIECISAFuncionario: Error al tratar de desbloquear el documento en el proceso del pago con tarjeta funcionario. IdLiquidacion: "+liquidacionInfo.getIdLiquidacion() + " error: " + ex.getMessage());
                        request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                        return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                    throw new Exception("Error al presentar la liquidacion" + presentacionInfo.getIdLiquidacion() + " tras el pago con tarjeta TPV PINPAD correcto");
                }   
                
                
            }else{
                
            	codigoError = codError;
                errorDesc = descrpErrorXml;
            	
                logger.debug("presentacionTPVIECISAFuncionario: Error en el pago con tarjeta por PINPAD: Liquidacion: " +idLiquidacion+ ". Codigo de Error: "+ codigoError + ". Descripcion: " + errorDesc);
                //Debo de devolver el documento al estado original
                //Ponemos la liquidacion en Error T�cnico en pago
                presentacionInfo.setIdEstadoActual(Constantes.ESTADO_ERROR_EN_PAGO);
                presentacionInfo.setMotivoLiquidacion(errorDesc + " (" + codigoError + ")");
                presentacionInfo.setLiteralEntidad("");
                try{
                    presentacionInfo.setFechaPago(fechaOperacionPago);
                    if (userProfile == null){
                        //Recupero el usuario ya grabado en BD
                        codUsuario = liquidacionInfo.getIdUsuario();
                        userProfile = getContratoBLJBean(request).getUserProfile(String.valueOf(codUsuario));
                    }else{
                        userProfile.setCodEntidad(0);
                        userProfile.setCodigoIban("");
                    }
                    //Obtengo el id de evolucion ultimo referente a esa liquidacion
                    idEvolucion = getLiquidacionBLJBean(request).obtenerUltimoIdEvolucion(userProfile,liquidacionInfo);
                    presentacionInfo.setIdEvolucion(idEvolucion);
                    //Cargo el literal
                    TipoEstadoLiquidacionInfo estadoLiquidacionInfo = getPresentacionBLJBean(request).getTipoEstadoLiquidacion(userProfile, presentacionInfo.getIdEstadoActual());
                    literalEstadoActual = getSessionJBean(request).getLiteral(estadoLiquidacionInfo.getLiteral());
                    presentacionInfo.setLiteralEstadoActual(literalEstadoActual);
                    //Actualizo sn_webliq y sn_evoliq
                    ResultInfo resultInfo = completarDatosEvolucion(request,userProfile,presentacionInfo);
                    if(resultInfo == null || (resultInfo != null && resultInfo.getError()) ){
                        logger.error("presentacionTPVIECISAFuncionario: Se ha producido un error en la actualizacion del estado del documento: " + presentacionInfo.getIdLiquidacion());
                    }
                    
                    try {
                        desbloquear(request, liquidacionInfo.getIdLiquidacion());
                    } catch (Exception e){
                        logger.error ("presentacionTPVIECISAFuncionario: Error al tratar de desbloquear el documento en el proceso del pago con tarjeta funcionario. IdLiquidacion: "+liquidacionInfo.getIdLiquidacion());
                        request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                        return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                    
                }catch(Exception e){
                    logger.debug("presentacionTPVIECISAFuncionario: Error: " + e.toString());
                    //Se desbloquea el documento
                    try {
                        desbloquear(request, liquidacionInfo.getIdLiquidacion());
                    } catch (Exception ex){
                        logger.error ("presentacionTPVIECISAFuncionario: Error al tratar de desbloquear el documento en el proceso del pago con tarjeta funcionario. IdLiquidacion: "+liquidacionInfo.getIdLiquidacion() + " error: " + ex.getMessage());
                        request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                        return getUrl(Constantes.MOSTRAR_ERROR);
                    }
                    throw new Exception("Error en el trato de la liquidacion" + presentacionInfo.getIdLiquidacion() + " tras el pago con tarjeta erroneo");
                }
            }
                
            //Aplicaciones Externas
            boolean grabado = false;
	        boolean estadoFinal = false;
	        if(presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO 
	                || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
	                || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
	                || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_NRC)
	        	estadoFinal = true;     
	        
	        String aplicacion = getPresentacionBLJBean(request).obtenerNombreAplicacion(liquidacionInfo.getIdLiquidacion(), userProfile, estadoFinal);
	        
	        //Si viene de PLAGES (GESTORIA) tendremos que redireccionar a PLAGES
	        String accesoPlages = (String)request.getSession().getAttribute("accesoPlages");
	        logger.debug("Viene de PLAGES?: " + accesoPlages);
	        
	        //Si la peticion es externa
        	if("true".equals(accesoPlages)) {
	        	try{
		        	request.setAttribute("aplicacion","GESTORIA");
		        	boolean hayUrlRecibo =devolverUrlRecibo(request, userProfile, liquidacionInfo);     
		    		if(hayUrlRecibo){
		    			//Como hay urlRecibo, redirecciono
						if(request.getAttribute("urlRecibo") != null){
							//Llamada la pl de KGESTORIA.factdocu para guardar los cambios del proceso de tarjeta
		                    logger.debug ("presentacionTPVIECISAFuncionario: Actualizamos el documento en la plataforma de Gestor�a");                            	
		                    getLiquidPreseBLJBean(request).actualizarGestoria(userProfile, presentacionInfo.getIdLiquidacion(), presentacionInfo.getIdEstadoActual(),null);
		                    
							// Redirecciono con el atributo tarjeta=true, para que plages sepa que venimos de pago con tarjeta
							String urlRecibo = (String)request.getAttribute("urlRecibo");
							request.removeAttribute("urlRecibo");
							urlRecibo = urlRecibo + "&tarjeta=true";
							request.setAttribute("urlRecibo",urlRecibo);
							logger.debug("presentacionTPVIECISAFuncionario: Redirecciono a la url: " + (String)request.getAttribute("urlRecibo"));
							request.setAttribute("aplicacion","GESTORIA");
							return getUrl(Constantes.URL_RECIBO_TARJETA_FRAME);
						}else{
							throw new Exception("No existe url de recibo");
						}
		    		}else{
		    			logger.error("presentacionTPVIECISAFuncionario: No hay direccion de env�o en BD para redireccionar a GESTORIA (PLAGES), vamos a error gen�rico");
		                return getUrl(Constantes.MOSTRAR_ERROR);
		    		}
	        	}catch(Exception e){
	        		logger.error("presentacionTPVIECISAFuncionario: Error en el env�o del resultado a PLAGES: " + e.toString());
	                return getUrl(Constantes.MOSTRAR_ERROR);
		    	}
	        }else{
	        	String urlGrabacion = getPresentacionBLJBean(request).obtenerURLGrabacion(aplicacion, userProfile);
	        	boolean hayUrlRecibo =devolverUrlRecibo(request, userProfile, liquidacionInfo);     
	        	
	            if(urlGrabacion != null && !"".equals(urlGrabacion)){
	                //Hacer la peticion de envio a dicha url
	                try{
		            	CifradoInfo datosCifrado = getPresentacionBLJBean(request).obtenerDatosCifrado(aplicacion, userProfile);
		                enviarUrlGrabacion(datosCifrado, urlGrabacion, presentacionInfo);
	                }catch(Exception e){
	                	 logger.error("Error en el env�o a la url de grabaci�n de la aplicacion: " + aplicacion + " a la url: " + urlGrabacion);
	                	 //throw new Exception("Error en el env�o a la URL: " + urlGrabacion);
	                }    
		                //Envio de la confirmacion de grabacion
		                presentacionInfo.setEntrega(1);
	                try{
	                    boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
	                    if(!result){
	                        throw new Exception("Error en la actualizacion de idEntrega");
	                    }else{
	                        grabado = result;
	                    }
	                }catch(Exception e){
	                    logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
	                    throw new Exception(e);
	                }
	            }
	            
	            if(hayUrlRecibo){
	            	boolean estadoFinalRecibo = false;
                    if(presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO 
                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_NRC)
                        estadoFinalRecibo = true;
                    String aplicacionRecibo = getPresentacionBLJBean(request).obtenerNombreAplicacion(presentacionInfo.getIdLiquidacion(), userProfile, estadoFinalRecibo);
                    //Creacion del String encriptado a partir del DocRespuesta que viene del jar de tasas
                    DocumentoRespuesta docRes = enviarUrlRecibo(request,true,presentacionInfo,presentacionInfo.getIdEstadoActual(),presentacionInfo.getReferencia());
                    String xmlEncriptado = obtenerDocResEncriptado(request,docRes,aplicacionRecibo);
                    String urlDst;
                    if(xmlEncriptado != null){
                        request.setAttribute("datosEncriptados", xmlEncriptado);
                        urlDst = getUrl(Constantes.URL_RECIBO);
                    }else{
                        logger.error("Ha habido un error al intentar encriptar el docRespuesta que debemos de enviar para la aplicacion: " + aplicacionRecibo);
                        urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                    }   
                    //Envio de la confirmacion de recibo
                    if(grabado){
                        presentacionInfo.setEntrega(2);
                    }else
                        presentacionInfo.setEntrega(3);
                    try{
                        boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
                        if(!result){
                            throw new Exception("Error en la actualizacion de idEntrega");
                        }
                    }catch(Exception e){
                        logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
                        throw new Exception(e);
                    }
                    return urlDst;
	            }
	            
	            //Si no hay aplicacion externa, pantalla de resultado.
	            try{
	                if(presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
	                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
	                        || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO){
	                    request.getSession().setAttribute("errorEnProceso",new ResultInfo(false,"",""));
	                }else{
	                    request.getSession().setAttribute("errorEnProceso",new ResultInfo(true,"",""));
	                }
	            }catch(Exception e){
	                logger.error("presentacionTPVIECISAFuncionario: Error en la distincion de estado del documento: " + e.toString());
	                return getUrl(Constantes.MOSTRAR_ERROR);
	            }   
	            
	            //Me preparo para irme a la presentacion del resultado en funcionario
	            request.getSession().setAttribute("presentacionInfo",presentacionInfo);
	            request.setAttribute("pago","");
	            request.setAttribute("nombreEntidadFinanciera","");
	            //Borro la entidad financiera para que no la muestre en la jsp de resultado.
	            request.getSession().removeAttribute("nombreEntidadFinanciera");
	            request.setAttribute("pagoTarjeta","");
	            request.setAttribute("ccc","");
	            return getUrl(Constantes.PRESENTACION_SIMPLIFICADA_RESULTADO);
	        }  	
	        	
        } catch(Exception e){
            logger.error("presentacionTPVIECISAFuncionario: Error no controlado" + e.toString());
            try{
                if(presentacionInfo.getIdEstadoActual() != Constantes.ESTADO_PAGADO){
                //Cargo el literal
                TipoEstadoLiquidacionInfo estadoLiquidacionInfo = getPresentacionBLJBean(request).getTipoEstadoLiquidacion(userProfile, presentacionInfo.getIdEstadoActual());
                literalEstadoActual = getSessionJBean(request).getLiteral(estadoLiquidacionInfo.getLiteral());
                presentacionInfo.setLiteralEstadoActual(literalEstadoActual);
                }
            }catch(Exception ex){
                logger.error("presentacionTPVIECISAFuncionario: Error en la obtencion del estado del documento a mostrar");
            }
            return getUrl(Constantes.MOSTRAR_ERROR);
        }
    }
    
    private void borraVariablesSesion(HttpServletRequest request, UserProfileInfo userProfile){
        
        HashMap<String, DatosAFirmar> listaIds = (HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS);
        if ((null != listaIds) && (listaIds.size() == 0)) {
            request.getSession().removeAttribute(Constantes.LISTA_IDS);
        }
        
        if (null != request.getSession().getAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE)){
            request.getSession().removeAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE); 
        }   
    }
    
    static private UserProfileInfo getUserProfile(HttpServletRequest request) {
    
        Object sessionJBean = request.getSession().getAttribute("sessionJBean");
        
        if (sessionJBean instanceof SessionJBean) {
            return ((SessionJBean) sessionJBean).getUserProfile();
        } else {
            return ((SessionAdminJBean) sessionJBean).getUserProfile();
        }
    }

protected ResultInfo cargarDatosComboOFPresentacion (HttpServletRequest request, UserProfileInfo userProfile) {
        
        ComboInfo comboCodigoTerritorial = (ComboInfo) getServletContext().getAttribute("comboCodigoTerritorial");
        ResultInfo res = new ResultInfo();
                
        if (comboCodigoTerritorial==null) {
                CodigoTerritorialInfoSet codigoTerritorialInfoSet = getPresentacionBLJBean(request).getCodigosTerritoriales(userProfile,new CodigoTerritorialInfo(),null);
                ArrayList arrayList;
                String[] values;
                String[] texts;
                boolean[] selected;

                // -------------------------------------------------------------- Combo de tipos de entidades financieras
                comboCodigoTerritorial = new ComboInfo();
                arrayList = codigoTerritorialInfoSet.getArrayList();
                values = new String[arrayList.size()+1];            // Vector de ID's (+ 1 para el valor inicial)
                texts  = new String[arrayList.size()+1];            // Vector de nombres
                selected = new boolean[arrayList.size()+1];         // Vector de seleccion
             
                //Se concatenara a value si es un estado finalizado o no y 2 en el caso de Todos
                values[0] = new String("");
                texts[0] = new String("Todos");
                selected[0] = false;
                boolean repetido;
                int numRepetidos = 0;
                for (int iRow = 0; iRow < arrayList.size(); iRow++) {
                
                    CodigoTerritorialInfo codigoTerritorialInfo = (CodigoTerritorialInfo) arrayList.get(iRow);
                    String nombreCodigoTerritorial = ((codigoTerritorialInfo.getLitofpreObject() != null) ? codigoTerritorialInfo.getLitofpre() : "" );
                    String idCodigoTerritorial = ((codigoTerritorialInfo.getCdterdocObject() != null) ? String.valueOf(codigoTerritorialInfo.getCdterpre()) : "" );
                    
                    repetido = false;
                    for(int i = 0; i < iRow; i++){
                        if (nombreCodigoTerritorial.equals(texts[i])){
                            repetido = true;
                            break;
                        }
                    }
                    if (repetido == false){
                        values[iRow+1] = idCodigoTerritorial;                   
                        texts[iRow+1] = nombreCodigoTerritorial;
                        selected[iRow+1] = false;
                    }
                    else{
                        values[iRow+1] = "";                    
                        texts[iRow+1] = "";
                        selected[iRow+1] = false;
                        numRepetidos++;
                    }
                }

                String[] valuesFinal = new String[arrayList.size() - numRepetidos + 1];         // Vector de ID's (+ 1 para el valor inicial)
                String[] textsFinal  = new String[arrayList.size() - numRepetidos + 1];         // Vector de nombres
                boolean[] selectedFinal = new boolean[arrayList.size() - numRepetidos + 1];         // Vector de seleccion
                    
                if (numRepetidos > 0){  
                    int j = 0;
                    for(int i = 0; i<arrayList.size(); i++){
                        if(!"".equalsIgnoreCase(values[i])){
                            valuesFinal[j] = values[i];
                            textsFinal[j] = texts[i];
                            selectedFinal[j] = selected[i];
                            j++;
                        }
                        if("".equalsIgnoreCase(values[i]) && "Todos".equalsIgnoreCase(texts[i])){
                            valuesFinal[j] = values[i];
                            textsFinal[j] = texts[i];
                            selectedFinal[j] = selected[i];
                            j++;
                        }
                        
                    } 
                }

                comboCodigoTerritorial.setName("comboCodigoTerritorial");
                comboCodigoTerritorial.setId("comboCodigoTerritorial");
//              comboCodigoTerritorial.setEvents("");
                comboCodigoTerritorial.setCssClass("codterr");
                comboCodigoTerritorial.setMultiple(false);
                comboCodigoTerritorial.setSize("1");
                if (numRepetidos > 0 ){
                    comboCodigoTerritorial.setOptionsValue(valuesFinal);
                    comboCodigoTerritorial.setOptionsText(textsFinal);
                }
                else{
                    comboCodigoTerritorial.setOptionsValue(values);
                    comboCodigoTerritorial.setOptionsText(texts);
                }
                request.getSession().setAttribute("comboCodigoTerritorial",comboCodigoTerritorial);
        } else {
            request.getSession().setAttribute("comboCodigoTerritorial",comboCodigoTerritorial);
            res.setError(false);
        }

        return res;
    
    }
    
    /**
     * PAGO PRESENTACION LIQUIDACION
     * 
     * @param request
     * @param userProfile
     * @param presentacionInfo
     * @return
     */
    private PresentacionInfo pagarPresentar(HttpServletRequest request, UserProfileInfo userProfile, PresentacionInfo presentacionInfo){
    
        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        ResultInfo resultado = null;
        int bloqueado= 1;
        String nombreEntidadFinanciera = null;
        try{
            double importe = presentacionInfo.getTotalIngresar();
            
            try{
                bloqueado = bloquear(request, presentacionInfo.getIdLiquidacion(), Constantes.GESTION_PAGO_ERROR);
                logger.trace("Bloqueo del documento"+presentacionInfo.getIdLiquidacion());
                if (bloqueado != 0 ){
                    resultado = new ResultInfo(true,Constantes.GESTION_PRES_ERROR,getSessionJBean(request).getLiteral(Constantes.GESTION_PAGO_ERROR));
                }else{
            
                    if (importe <= 0){
                    }else{
                        //GESTION FECHA PRESENTACION                                                
                        if (null == presentacionInfo.getFechaPresentacionObject() ){
                            presentacionInfo.setFechaPresentacion(new java.util.Date());
                        }
                        
                        EntidadFinancieraInfo entidadFinancieraInfo = null;
                        if(presentacionInfo.isPagoDiferido()){                
							SimpleDateFormat fechaConFormato = new SimpleDateFormat("dd/MM/yyyy");
							String fecha = fechaConFormato.format(presentacionInfo.getFechaPago());
							fecha = fecha + " 00:00:00";
                            entidadFinancieraInfo = getPresentacionBLJBean(request).getEntidadFinanciera(userProfile,presentacionInfo.getCodEntidad(),fecha);
                        }else{
                            entidadFinancieraInfo = getPresentacionBLJBean(request).getEntidadFinanciera(userProfile,presentacionInfo.getCodEntidad());
                        }
                        
                        nombreEntidadFinanciera = request.getParameter("nombreEntidadFinanciera");
                        if (!Utilidades.estaVacia(nombreEntidadFinanciera)) {
							presentacionInfo.setLiteralEntidadObject(nombreEntidadFinanciera);
						}                              
                        PresentacionInfo presentacionInfoAux = presentacionInfo; 
                        presentacionInfo=peticionPago(request, userProfile,presentacionInfoAux,entidadFinancieraInfo,urlDst,"","","","");
                    }

                    //6. PRESENTAR
                    boolean estaPagada = ((presentacionInfo.getNrcObject () != null) && (!"".equals(presentacionInfo.getNrc())));
                    if ((presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_TECNICO && estaPagada) 
                    		|| (presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR && presentacionInfo.getTotalIngresar() <= 0.0)){                
                        logger.trace("Comienza el proceso de presentacion. Fecha Presentacion vale: " + presentacionInfo.getFechaPago());               
                        logger.trace("################################");
                        logger.trace("##### COMIENZO PRESENTACION ####");
                        logger.trace("################################");               
                        presentacionInfo.setFechaPresentacion(new java.util.Date());
                        presentacionInfo = presentarSUR(request, userProfile, presentacionInfo);
                        
                        boolean ok = true;
                        if (presentacionInfo.getResultInfo().getError())
                            ok = false;
                        //Controlamos si ha habido error presentando.
                        if(ok){
                            resultado = new ResultInfo(false, "", "");
                        }else{
                            resultado = new ResultInfo(true, "TEL006",getSessionJBean(request).getLiteral("TEL006"));       
                            presentacionInfo.setLiteralEstadoActual(getSessionJBean(request).getLiteral("TEL006"));
                        }
                    }else{
                        if(presentacionInfo.getLiteralEstadoActual() != null)
                            resultado = new ResultInfo(true, presentacionInfo.getLiteralEstadoActual().toUpperCase(), presentacionInfo.getMotivoLiquidacion());
                        else
                            resultado = new ResultInfo(true, "", presentacionInfo.getMotivoLiquidacion());
                    }
                }
            }catch(Exception e){
                logger.error(e.getMessage(), e);
                urlDst = getUrl(Constantes.MOSTRAR_ERROR);
            }finally{
                try{
                    // Si el documento fue bloqueado, procedemos al desbloqueo
                    if(bloqueado == 0){
                        bloqueado = desbloquear(request, presentacionInfo.getIdLiquidacion());
                        logger.trace("Desbloqueo del documento"+presentacionInfo.getIdLiquidacion());
                    }

                }catch (Exception e){
                    logger.error(e.getMessage(), e);
                    request.setAttribute("resultInfo", new ResultInfo(true,"BLOQUEO","ERROR_BLOQUEO"));
                    urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                }
            }
                                        
            // Finalmente, si no ha habido errores extra�os envio a la pantalla de resumen del pago/presentacion
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            resultado =new ResultInfo(true, "ERR000", "Error desconocido");
            request.setAttribute("presentacionInfoError", presentacionInfo);
            urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        }
        
        presentacionInfo.setResultInfo(resultado);
        return presentacionInfo;
}

    //Para acceder a la consulta de liquidaciones de SURNET desde la pagina de presentacionSimplificada
    //Evita problemas con los frames.
    private String accesoSURNET(HttpServletRequest request, UserProfileInfo userProfile){
        
        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        String aplicacion = "";
        try{
        	aplicacion = (String)request.getSession().getAttribute("aplicacion");
            ConsultaLiquidacionInfo liquidacionInfo = (ConsultaLiquidacionInfo)request.getSession().getAttribute("liquidacionInfo");
            
            logger.debug("Aplicacion: " + aplicacion);
            
        	//Desde PAgo Simplificado con empleado p�blico, cuando se realiza a trav�s de una aplicacion externa, 
        	//hay que detectarlo y responder a la aplicaci�n externa
        	String urlGrabacion = getPresentacionBLJBean(request).obtenerURLGrabacion(aplicacion, userProfile);
            
        	logger.debug("urlGrabacion: " + urlGrabacion);
        	
            if(urlGrabacion != null && !"".equals(urlGrabacion)){
            	LiquidacionInfo liquidacion = getPresentacionBLJBean(request).getLiquidacion(userProfile, liquidacionInfo.getIdLiquidacion());
            	PresentacionInfo presentacionInfo = new PresentacionInfo(liquidacion);
            	logger.debug("Envio a urlGrabacion");
                //Hacer la peticion de envio a dicha url
                CifradoInfo datosCifrado = getPresentacionBLJBean(request).obtenerDatosCifrado(aplicacion, userProfile);
                enviarUrlGrabacion(datosCifrado, urlGrabacion, presentacionInfo);
                //Envio de la confirmacion de grabacion
                presentacionInfo.setEntrega(1);
                try{
                    boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
                    if(!result){
                        throw new Exception("Error en la actualizacion de idEntrega");
                    }
                }catch(Exception e){
                    logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
                    throw new Exception(e);
                }
            }
        	
            logger.debug("Envio a urlREcibo");
        	urlDst = salirAplicacion(request,userProfile);
        	logger.debug("urlDst: " + urlDst);
        	//Si no existe url de recibo ni de grabaci�n, nos vamos a loa pantalla de inicio de Plataforma 
        	if(urlDst != null && urlDst.equals(getUrl(Constantes.MOSTRAR_ERROR)) 
        			&& Utilidades.estaVacia(urlGrabacion)){
        		logger.debug("Envio a inicio Plataforma normal");
        		eliminarSessionAttributeSimp(request);
                
                // Configura el frameset para que aparezca el men� particular / profesional
                ContratoInfo contratoInfo = getContratoBLJBean(request).getContrato(userProfile.getIdContrato());
                request.getSession().setAttribute("cif",contratoInfo.getCif());
                request.getSession().setAttribute("razonSocial",contratoInfo.getDelegacion());
                String menu =  getUrl("MENU_PROFESIONAL");
                
                request.setAttribute("menu",menu);
                userProfile.setAutorizacion(new AutorizacionInfo());
                urlDst = getUrl(Constantes.ACCESO_SURNET);	
        	}
        	
        }catch(Exception e){
            logger.error(e.getMessage(), e);
            urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        }
        return urlDst;
    }
    
    
    /**
      * Salir aplicacion externa con error cancelado por el usuario.
      * 
      * @param request the request
      * @param userProfile the user profile
      * 
      * @return the string
      */
     private String salirAplicacion(HttpServletRequest request, UserProfileInfo userProfile){
        
        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        PresentacionInfo presInfo = new PresentacionInfo();
        String aplicacion = "";
        String referencia = "";
        String idLiquidacion = "";
        DocumentoRespuesta docRes = new DocumentoRespuesta();
        
        try{
            aplicacion = (String)request.getSession().getAttribute("aplicacion");
            ConsultaLiquidacionInfo liquidacionInfo = (ConsultaLiquidacionInfo)request.getSession().getAttribute("liquidacionInfo");
            if(liquidacionInfo != null){
                referencia = liquidacionInfo.getReferencia();
                idLiquidacion = liquidacionInfo.getIdLiquidacion();
            } 
            if(aplicacion != null){
                presInfo.setNombreAplicacion(aplicacion);
            }else{
                logger.error("La aplicacion que tenemos en session es nula");
                throw new Exception("No existe aplicacion.");
            }
            if(presInfo != null && devolverUrlRecibo(request, userProfile, presInfo)){
                logger.debug("Informando del error a la aplicacion externa: "+presInfo.getNombreAplicacion());
                //Creacion del String encriptado a partir del DocRespuesta que viene del jar de tasas
                docRes.setNumeroDocumento(idLiquidacion);
                docRes.setEstado("ERROR");
                docRes.setError("El proceso de pago/presentaci�n ha sido cancelado por el usuario");
                docRes.setCodigoEstadoPago(0);
                docRes.setReferenciaExterna(referencia);
                String xmlEncriptado = obtenerDocResEncriptado(request,docRes,presInfo.getNombreAplicacion());
                if(xmlEncriptado != null){
                    request.setAttribute("datosEncriptados", xmlEncriptado);
                    urlDst = getUrl(Constantes.URL_RECIBO);
                }else{
                    logger.error("Ha habido un error al intentar encriptar el docRespuesta que debemos de enviar para la aplicacion: " + presInfo.getNombreAplicacion());
                    urlDst = getUrl(Constantes.MOSTRAR_ERROR);
                }   
             }else{
                 logger.error("La url de recibo para la aplicacion " + aplicacion + " , es nula o ha habido un error.");
                 throw new Exception("No existe direcci�n de reenvio para la aplicacion: " + aplicacion + ".");
             }

        }catch(Exception e){
            logger.error(e.getMessage(), e); 
            if(e != null && e.getMessage() != null)
                request.setAttribute("resultInfo",new ResultInfo(true,"ERR000",e.getMessage()));
            return urlDst;
        }
        return urlDst;
    }

    private NumeroSURWebPetInfo getModeloAutorizacion(HttpServletRequest request,UserProfileInfo userProfile, ConsultaLiquidacionInfo consultaLiquidacionInfo, ModeloAmpliadoInfo modeloAmpliadoInfo){
        NumeroSURWebPetInfo numeroSURWebPetInfo=null;
        String modeloAutorizacion="AU1";
        String version="1";
        String rango="01";
        numeroSURWebPetInfo=new NumeroSURWebPetInfo(modeloAutorizacion,version,rango);
        try{
            //AQUI UNA LLLAMADA PARA obtener el modelo de la autorizacion y MODIFICAR EL PRESENTADOR si es necesario
            numeroSURWebPetInfo=getLiquidacionBLJBean(request).getNumeroSURWebPetInfo(userProfile,consultaLiquidacionInfo,modeloAmpliadoInfo);

        }catch (Exception e){
            logger.error("Error obteniendo el modelo de autorizacion.", e); 
        }
        
        
        return numeroSURWebPetInfo;
        
    }
    
    private boolean getEsRepresentante(HttpServletRequest request,UserProfileInfo userProfile, ConsultaLiquidacionInfo consultaLiquidacionInfo){
        boolean esRepresentante = false;
        try{
            esRepresentante=getLiquidacionBLJBean(request).getEsRepresentante(userProfile,consultaLiquidacionInfo);

            
            
        }catch (Exception e){
            logger.error("Error obteniendo el modelo de autorizacion.", e); 
        }
        
        
        return esRepresentante;
        
    }
    
    
    
    private String obtenerAnagramaCorto(HttpServletRequest request, UserProfileInfo userProfile, PresentacionInfo presentacion){
    
        String anagramaCorto = null;
        try{
            
            CasillaInfo casillaAnagramaCorto = new CasillaInfo();
            casillaAnagramaCorto.setNumeroCasilla(Constantes.NUMERO_CASILLA_ANAGRAMA_CORTO);
            casillaAnagramaCorto.setIdLiquidacion(presentacion.getIdLiquidacion());
            CasillaInfoSet casillaInfoSet = getPresentacionBLJBean(request).getCasillas(userProfile, casillaAnagramaCorto, null);
            if (casillaInfoSet.getArrayList().size() > 0){
                casillaAnagramaCorto = (CasillaInfo) casillaInfoSet.getArrayList().get(0);
                anagramaCorto = casillaAnagramaCorto.getValorCasilla();
            }       
        }catch(Exception e){
            logger.error("Error obteniendo el anagrama corto.", e);
            anagramaCorto = null;
        }
        return anagramaCorto;   
    }
    
    private byte[] generaAutorizacion(HttpServletRequest request, UserProfileInfo userProfile,ConsultaLiquidacionInfo consultaLiquidacionInfo, boolean datosModif)
        throws Exception{
    
        ModeloAmpliadoInfo modeloAmpliadoInfo = new ModeloAmpliadoInfo();
        HashMap<String, DatosAFirmar> listaIds = null;
        byte[] oPDF = null;
        byte[] confeccionPDF = null;
        double idCustodia;
        String idTransaccionFirma="";
        DatosAFirmar sDatos=null;
        String nombreDocumento=null;
        try{
            modeloAmpliadoInfo = getPresentacionBLJBean(request).getModelo(userProfile, consultaLiquidacionInfo.getIdModelo());
            long estadoLiquidBD = consultaLiquidacionInfo.getIdEstadoActual(); 
            if(estadoLiquidBD != Constantes.ESTADO_PENDIENTE_FIRMA){
                logger.error("Se ha intentado firmar una liquidacion en estado: " + estadoLiquidBD);
                request.setAttribute("destino", Utiles.getPathSiriAction() + "Liquidacion?" + BaseServletController.OPERATION + "="+ Constantes.BUSCAR);
                throw new ApplicationError(new ResultInfo(true, "PDF002", 
                getSessionJBean(request).getLiteral("PDF002")), new Exception("Se ha intentado firmar una liquidacion en estado: " + estadoLiquidBD));          
            }//Gestion Permisos: No tiene sentido ya este error
                /*else if (!permiso){   // Comprobamos que tenga permiso para gestionar liquidaciones.      
                    getLog(request).writeTrace(Log.ERROR, this, "inicioPresentacionSimplificada","El usuario no tiene permisos para firmar este tipo de modelo ");                      
                    throw new ApplicationError(new ResultInfo(true, "ERR508", 
                      getSessionJBean(request).getLiteral("ERR508")), 
                      new Exception("No tiene permisos para firmar este modelo "));}*/
            else if(modeloAmpliadoInfo.getActivo() != Constantes.ID_ESTADO_CONTRATO_ACTIVO
            		&&	modeloAmpliadoInfo.getActivo() != Constantes.ID_ESTADO_TABLA_ACTIVO_R40){ //Comprobamos que no este bloquedado el modelo
                logger.error("El modelo de la liquidacion no esta activo");                     
                throw new ApplicationError(new ResultInfo(true, "ERR513", 
                getSessionJBean(request).getLiteral("ERR513")), 
                new Exception("El modelo no esta activo "));
               }                        
            //Si no ha habido ninguna excepcion, el flujo sigue y procedemos a mostrar a
            //registrar la transaccion
            
            //11-05-2006 jatoribio: modificado para permitir mas modelo de autorizacion
            
            NumeroSURWebPetInfo numeroSURWebPetInfo = getModeloAutorizacion(request, userProfile, consultaLiquidacionInfo,modeloAmpliadoInfo);
            
            //Controlo si es pago mancomunado que s�lo permita la generaci�n de AU1 y AU2
            if(userProfile.getAutorizacion().isEsPagoMancomunado()){
                if(numeroSURWebPetInfo != null 
                        && !Constantes.AUTORIZACION_AU1.equals(numeroSURWebPetInfo.getIdModelo()) 
                        && !Constantes.AUTORIZACION_AU2.equals(numeroSURWebPetInfo.getIdModelo())){
                    logger.error("No se pudo obtener un modelo de autorizaci�n para el documento. Al ser un pago mancomunado no se permite el pago/presentaci�n de este documento con estos sujetos.");
                    throw new ApplicationError(numeroSURWebPetInfo.getResultInfo(),new Exception("No se pudo obtener un modelo de autorizaci�n para el documento. Al ser un pago mancomunado no se permite el pago/presentaci�n de este documento con estos sujetos."));
                }
            }
            
            if(numeroSURWebPetInfo.getResultInfo().getError()){
                //No se ha generado bien el modelo de autorizacion
                logger.error("No se pudo obtener un modelo de autorizaci�n para el documento.");
                throw new ApplicationError(numeroSURWebPetInfo.getResultInfo(),new Exception("No se pudo obtener un modelo de autorizaci�n para el documento."));               
            }
            userProfile.getAutorizacion().setNumeroSURWebPetInfo(numeroSURWebPetInfo);
            NumeroSURWebJBean numeroSURWebJBean = new NumeroSURWebJBean();
            //Preparo el AU7 para que me de un n�mero de AU7
            NumeroSURWebRespInfo numeroSURWebRespInfo = numeroSURWebJBean.getNumeroDocumento(new NumeroSURWebPetInfo(Constantes.AUTORIZACION_AU7,"1","01"));
            
            //Obtengo el PDF
            oPDF = generaPDFFuncionario(request, userProfile, consultaLiquidacionInfo);
                
            //Obtengo el nombre del fichero a partir del modelo, version y numDocumento de la autoliquidacion
            ModificacionPDF generadorPDF2 = new ModificacionPDF();
            String directorioConfiguracion = getServletContext().getRealPath(Constantes.DEFAULT_BASE_DIR);
            generadorPDF2.inicializarGeneradorPDF(directorioConfiguracion);
            
            
            ContratoInfo contratoInfo=getContratoBLJBean(request).getContrato(userProfile.getIdContrato());
            Map casillas= getCasillasModelo(request,consultaLiquidacionInfo, contratoInfo, userProfile, numeroSURWebRespInfo);
            //Diferencio si llamo con tarjeta o con CCC
            if( consultaLiquidacionInfo.getTarjeta() != null && consultaLiquidacionInfo.getTarjeta().equals("1")){
                confeccionPDF = generadorPDF2.confecionPDF(directorioConfiguracion + "/pdfs/"+numeroSURWebPetInfo.getIdModelo()+"_T_"+numeroSURWebPetInfo.getVersion()+"/modelo.xml", oPDF, casillas);
            }else{
            	if(userProfile.getAutorizacion().getNrc()!=null ? !userProfile.getAutorizacion().getNrc().isEmpty() : false) {
					if(userProfile.getAutorizacion().isJustificantePagoRecibido()) {
						//NRC FICTICIO
						confeccionPDF = generadorPDF2.confecionPDF(directorioConfiguracion + "/pdfs/"
								+ numeroSURWebPetInfo.getIdModelo() + "_NRC_FIC_" + numeroSURWebPetInfo.getVersion() + "/modelo.xml",
								oPDF, casillas);
					}else {
						//NRC
						confeccionPDF = generadorPDF2.confecionPDF(directorioConfiguracion + "/pdfs/"
								+ numeroSURWebPetInfo.getIdModelo() + "_NRC_" + numeroSURWebPetInfo.getVersion() + "/modelo.xml",
								oPDF, casillas);
					}
				}else {
	                //Distingo si la cuenta es mancomunada o no.
	                if(userProfile.getAutorizacion().isEsPagoMancomunado()){
	                    confeccionPDF = generadorPDF2.confecionPDF(directorioConfiguracion + "/pdfs/"+numeroSURWebPetInfo.getIdModelo()+"_CM_"+numeroSURWebPetInfo.getVersion()+"/modelo.xml", oPDF, casillas);
	                }else{
	                    confeccionPDF = generadorPDF2.confecionPDF(directorioConfiguracion + "/pdfs/"+numeroSURWebPetInfo.getIdModelo()+"_"+numeroSURWebPetInfo.getVersion()+"/modelo.xml", oPDF, casillas);
	                }
				}
            }   
            
            userProfile.getAutorizacion().setIdAutorizacion(numeroSURWebRespInfo.getNumeroDocumento());
            nombreDocumento = consultaLiquidacionInfo.getIdLiquidacion();
            
            try {
                ClienteFirma clienteFirma = ClienteFirmaFactory.crearClienteFirma(Utiles.getPropiedadesSiri().getString(Constantes.ID_APLICACION_FIRMA)); 
                idCustodia=clienteFirma.registrarDocumento(confeccionPDF, nombreDocumento, "PDF");
                //Genero los datos que tiene que firmar el usuario en el jsp
                sDatos = clienteFirma.generarDatosAFirmar(idCustodia);
                idTransaccionFirma = sDatos.getIdTransaccion();
            } catch (Exception e) {
                //Capturo cualquier excepci�n espec�fica del m�dulo de telventi
                throw new ApplicationError(new ResultInfo(true, "PDF009", 
                    getSessionJBean(request).getLiteral("PDF009")), e);
            }
            //Meto el id en la lista de ids guardados en sesion
            listaIds = (HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS);
            if (listaIds == null) {
                listaIds = new HashMap<String, DatosAFirmar>();
            }
            listaIds.put(idTransaccionFirma, sDatos);
            request.getSession().setAttribute(Constantes.LISTA_IDS, listaIds);
                
            ArrayList<String> documentosFirmaMultiple = new ArrayList<String>();
            documentosFirmaMultiple.add(consultaLiquidacionInfo.getIdLiquidacion());
            request.getSession().setAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE, documentosFirmaMultiple);
                           
            request.setAttribute("idFirma",idTransaccionFirma);
            request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, consultaLiquidacionInfo.getIdLiquidacion());
            
            //Controlar� si el que va a iniciar todo el proceso de firma (adjuntos y no adjuntos tiene una autorizaci�n definida como firma de servidor o no)
            //logger.debug("Alias de certificado de servidor." + contratoInfo.getAliasCert());
            userProfile.setAliasCert(contratoInfo.getAliasCert());
            //logger.debug("Alias de certificado de servidor." + userProfile.getAliasCert());
            
            
        }catch (Exception e){
            throw new ApplicationError(new ResultInfo(true, "PDF005", 
                    getSessionJBean(request).getLiteral("PDF005")), e);
        }
        return confeccionPDF;
    }
    
    //jatoribio 08-05-2006: genera un mapa de casillas dependiendo del modelo de autorizacion
    private Map getCasillasModelo(HttpServletRequest request,ConsultaLiquidacionInfo consultaLiquidacionInfo, ContratoInfo contratoInfo, UserProfileInfo userProfile, NumeroSURWebRespInfo numeroSURWebRespInfo){
        Map<String, Casilla> casillas = new HashMap<String, Casilla>();
        
        casillas.put("h1c007_1", new Casilla("1", "h1c007_1", contratoInfo.getDelegacion()));
        casillas.put("h1c001", new Casilla("1", "h1c001", numeroSURWebRespInfo.getNumeroDocumento()));          
        casillas.put("h1c005", new Casilla("1", "h1c005", consultaLiquidacionInfo.getNifSujetoPasivo()));
        casillas.put("h1c006", new Casilla("1", "h1c006", consultaLiquidacionInfo.getSujetoPasivo()));
        casillas.put("h1c007", new Casilla("1", "h1c007", contratoInfo.getDelegacion()));
        casillas.put("h1c008", new Casilla("1", "h1c008", consultaLiquidacionInfo.getIdLiquidacion()));
        casillas.put("h1c009", new Casilla("1", "h1c009", consultaLiquidacionInfo.getTotalIngresarFormateadoPDF()));
        
        if (!"1".equals(consultaLiquidacionInfo.getTarjeta().equals("1"))){
          //Metemos IBAN si viene, sino metemos CCC
            if(Utilidades.estaVacia(consultaLiquidacionInfo.getIban())){
                casillas.put("h1c010", new Casilla("1", "h1c010", consultaLiquidacionInfo.getCCC()));
            }else{
                casillas.put("h1c010", new Casilla("1", "h1c010", consultaLiquidacionInfo.getIban()));
            }
        }
        SimpleDateFormat simpleDate = new SimpleDateFormat(Constantes.FORMATO_FECHA_CONSULTA);
        casillas.put("h1c011", new Casilla("1", "h1c011", simpleDate.format(new Date(System.currentTimeMillis()))));
        casillas.put("h1c012", new Casilla("1","h1c012",userProfile.getAutorizacion().getNifPagador()));
        casillas.put("h1c013", new Casilla("1","h1c013",userProfile.getAutorizacion().getNombrePagador()));
        
        //Aqui podremos obtener las casillas correspondientes al representante2 si es mancomunada la cuenta
        if(userProfile.getAutorizacion().isEsPagoMancomunado()){
          casillas.put("h1c014", new Casilla("1","h1c014",userProfile.getAutorizacion().getNifPagador2()));
          casillas.put("h1c015", new Casilla("1","h1c015",userProfile.getAutorizacion().getNombrePagador2()));
        }
        
        //De momento el �rgano Gestor, hemos decidido que lo vamos a mostrar con el nombre de la Delegaci�n del contrato
        casillas.put("h1c000", new Casilla("1","h1c000",contratoInfo.getDelegacion()));
        
        return casillas;
    }   
 
    private void eliminarSessionAttributeSimp(HttpServletRequest request){
        
        if (null != request.getSession().getAttribute("codigoTerritorialOficinaPresentacion")){
            request.getSession().removeAttribute("codigoTerritorialOficinaPresentacion");
        }
        if (null != request.getSession().getAttribute("literalOficinaPresentacion")){
            request.getSession().removeAttribute("literalOficinaPresentacion");
        }
        if (null != request.getSession().getAttribute("literalOficinaPresentacionDocumento")){
            request.getSession().removeAttribute("literalOficinaPresentacionDocumento");
        }
        
        if (null != request.getSession().getAttribute("comboOficinaPresentacion")){
            request.getSession().removeAttribute("comboOficinaPresentacion");
        }
        if (null != request.getSession().getAttribute("codigosTerritorialesHash")){
            request.getSession().removeAttribute("codigosTerritorialesHash");
        }
        if (null != request.getSession().getAttribute("id_"+request.getSession().getAttribute("idFirma"))){
            request.getSession().removeAttribute("id_"+request.getSession().getAttribute("idFirma"));
        }
        if (null != request.getSession().getAttribute("idFirma")){
            request.getSession().removeAttribute("idFirma");
        }
        if (null != request.getSession().getAttribute("nombreEntidadFianciera")){
            request.getSession().removeAttribute("nombreEntidadFianciera");
        }
        if (null != request.getSession().getAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE)){
            request.getSession().removeAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE); 
        }
        if (null != request.getSession().getAttribute("codigoTerritorialOficinaPresentacion")){
            request.getSession().removeAttribute("codigoTerritorialOficinaPresentacion");   
        }
        if (null != request.getSession().getAttribute("liquidacionInfo")){
            request.getSession().removeAttribute("liquidacionInfo");
        }
        HashMap listaIds = (HashMap) request.getSession().getAttribute(Constantes.LISTA_IDS);
        if ((null != listaIds) && (listaIds.size() == 0)) {
            request.getSession().removeAttribute(Constantes.LISTA_IDS);
        }
        if (null != request.getSession().getAttribute("nombreEntidadFianciera")){
            request.getSession().removeAttribute("nombreEntidadFianciera"); 
        } 
        if (null != request.getSession().getAttribute("liquidacionImportada")){
            request.getSession().removeAttribute("liquidacionImportada");   
        } 
        if (null != request.getSession().getAttribute("pagoSimplificado")){
            request.getSession().removeAttribute("pagoSimplificado");
        }
        if (null != request.getSession().getAttribute("simplificado")){
            request.getSession().removeAttribute("simplificado");
        }
        if (null != request.getSession().getAttribute("vehiculo")){
            request.getSession().removeAttribute("vehiculo");
        }
        if (null != request.getSession().getAttribute("userProfile")){
            request.getSession().removeAttribute("userProfile");
        }
        if (null != request.getSession().getAttribute("accesoExterno")){
            request.getSession().removeAttribute("accesoExterno");
        }
        if (null != request.getSession().getAttribute("accesoPlages")){
            request.getSession().removeAttribute("accesoPlages");
        }
    }
    
    /**
     * DOCUMENTAME!
     *
     * @param request DOCUMENTAME!
     * @param userProfile DOCUMENTAME!
     * @param consultaLiquidacionInfo DOCUMENTAME!
     *
     * @return DOCUMENTAME!
     *
     * @throws Exception DOCUMENTAME!
     */
    public ResultInfo actualizarEvolucionBDFuncionario(HttpServletRequest request,
        UserProfileInfo userProfile, ConsultaLiquidacionInfo consultaLiquidacion, LiquidacionInfo liquidacionInfo) throws Exception{
        ResultInfo resultInfo = new ResultInfo(true,"",""); 
        resultInfo = getLiquidacionBLJBean(request).insertaEvolucionFuncionario(userProfile, consultaLiquidacion, liquidacionInfo);
    
        return resultInfo;
    }

    private String informacion (HttpServletRequest request, UserProfileInfo userProfileInfo){
        return obtenerInformacion (request,userProfileInfo);
    }

private String inicioPresentacionSimplificadaSinFirma(HttpServletRequest request, UserProfileInfo userProfile){
        
        String sEstado = (String)request.getAttribute(Constantes.PDF_ESTADO);
        //String idLiquidacion = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
        String idDocumentoFirmar = (String)request.getAttribute("id");
        String sReferencia = (String)request.getAttribute(Constantes.PDF_REFERENCIA);

        String urlDst = getUrl(Constantes.MOSTRAR_ERROR);
        String idTransaccion="";
        HashMap<String, DatosAFirmar> listaIds = null;
        byte[] oPDF = null;
        ModeloAmpliadoInfo modeloAmpliadoInfo = new ModeloAmpliadoInfo();
        ConsultaLiquidacionInfo consultaLiquidacionInfo =  new ConsultaLiquidacionInfo();
        
        try{            
                                
            String idLiquidacion = idDocumentoFirmar;
               
            //Obtenemos la liquidacion para poder obtener su modelo correspondiente.
            LiquidacionInfo liquidacion = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
            modeloAmpliadoInfo = getPresentacionBLJBean(request).getModelo(userProfile, liquidacion.getIdModelo());

            //Comprobamos que el documento que se va a firmar no este firmado ya consultando su estado directamente de la base de datos.
            long estadoLiquidBD = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion).getIdEstadoActual(); 
            if(modeloAmpliadoInfo.getActivo() != Constantes.ID_ESTADO_CONTRATO_ACTIVO
            	 &&	modeloAmpliadoInfo.getActivo() != Constantes.ID_ESTADO_TABLA_ACTIVO_R40){ //Comprobamos que no este bloquedado el modelo
                logger.error("El modelo de la liquidacion "+idLiquidacion+"no esta activo");                        
                throw new ApplicationError(new ResultInfo(true, "ERR513", 
                getSessionJBean(request).getLiteral("ERR513")), 
                new Exception("El modelo no esta activo "));                                                      
               }                        
            //Obtengo el PDF
            //String idLiquidacion = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);
            String[] idsDocumentosFirmar = new String[1];
            idsDocumentosFirmar[0] = idDocumentoFirmar;        
            
            String sXML = null;
            
            sXML = getLiquidacionBLJBean(request).generaXML(userProfile, idsDocumentosFirmar, 
                sReferencia, Integer.parseInt(sEstado));
            sXML = Utiles.cifra(sXML.getBytes(Constantes.ENCODING_XML_ISO));
            
            //Nueva distincion para que se muestre una marca de agua si el estado del documento es no final
            int idEstado = Integer.parseInt(sEstado);
            if ((idEstado != Constantes.ESTADO_PRESENTADO) && (idEstado != Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR) && (idEstado != Constantes.ESTADO_PAGADO) && (idEstado != Constantes.ESTADO_ERROR_NRC) ) {
                oPDF = Utiles.generaPDFMarca(sXML);
            }else{
                oPDF = Utiles.generaPDF(sXML);
            }   

            if (oPDF.length == 0) {
                //Se produjo un error en la generacion del PDF.
                throw new ApplicationError(new ResultInfo(true, "PDF010", 
                    getSessionJBean(request).getLiteral("PDF010")), 
                    new Exception("Se produjo un error en el servidor de PDFs"));
            }
            //Obtengo el nombre del fichero a partir del modelo, version y numDocumento de la autoliquidacion            
            LiquidacionInfo oLI = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idDocumentoFirmar);         
                       
            //Recupero el id de firma de session
            idTransaccion = (String)request.getSession().getAttribute("idFirma");
            //Meto el id en la lista de ids guardados en sesion
            listaIds = (HashMap<String, DatosAFirmar>) request.getSession().getAttribute(Constantes.LISTA_IDS);
            if (listaIds == null) {
                listaIds = new HashMap<String, DatosAFirmar>();
            }
            // Si lo ponemos a null, dar� error y no mostrar� el pdf en el m�todo descargarIdFirma
            listaIds.put(idTransaccion, new DatosAFirmar()); 
            request.getSession().setAttribute(Constantes.LISTA_IDS, listaIds);
            
            ArrayList<String> documentosFirmaMultiple = new ArrayList<String>();
            documentosFirmaMultiple.add(idDocumentoFirmar);
            request.getSession().setAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE, documentosFirmaMultiple);
                        
            request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, idDocumentoFirmar);
            // colocamos el atributo idFirma para la jsp del frame de pdf
            request.setAttribute("idFirma",idTransaccion);
            request.getSession().setAttribute("pagoSimplificado","true");
            
            //Obtener las oficinas de presentacion y el combo con las entidades financieras.
            cargarDatosComboEntidadesFinancieras(request, userProfile);
            
            // Si estamos en pago diferido eliminamos las entidades financieras que no permitan el pago diferido
            boolean esPagoDiferido = liquidacion.isPagoDiferido();
            if (esPagoDiferido && userProfile.getIdTipoUsuario() != Constantes.ID_TIPO_FUNCIONARIO){
            	ArrayList arrayEntidades = null;
            	EntidadFinancieraInfoSet entidadInfoSet = null;
            	if ((userProfile.getIdTipoUsuario()==Constantes.ID_TIPO_FUNCIONARIO) || 
        				(String)request.getSession().getAttribute("pagoSimplificado")!=null){
            		entidadInfoSet = (EntidadFinancieraInfoSet)request.getSession().getAttribute("infoSetEntidadFinanciera");
            	}else{
        			entidadInfoSet =  (EntidadFinancieraInfoSet)request.getAttribute("infoSetEntidadFinanciera");
            	}
            	if (null != entidadInfoSet){
            		arrayEntidades = (ArrayList)entidadInfoSet.getArrayList();
            		// Si es pago con NRC diferido miro si existen cuentas bancarias que permitan el pago con NRC diferido
                	Iterator<EntidadFinancieraInfo> it = arrayEntidades.iterator();
                	while (it.hasNext()){
                		EntidadFinancieraInfo entidadInfo = (EntidadFinancieraInfo)it.next();
                		if (!entidadInfo.isPagoDiferido()){
                			// la elimino del combo
                			it.remove();
                		}
                	}
                	if (null == arrayEntidades || (null != arrayEntidades && arrayEntidades.size() < 1)){
                		request.setAttribute("resultInfo", new ResultInfo (true, null, "En estos momentos ninguna de las entidades financieras adscritas a la Plataforma Telem�tica de Pago admite el pago con NRC diferido. Disculpe las molestias."));
                		throw new Exception("Estamos en pago diferido y no existen endidades financieras que admitan pago diferido");
                	}else{
                		entidadInfoSet.setArrayList(arrayEntidades);
                		// almacenamos el nuevo array
                		if ((userProfile.getIdTipoUsuario()==Constantes.ID_TIPO_FUNCIONARIO) || 
                				(String)request.getSession().getAttribute("pagoSimplificado")!=null){
                    		request.getSession().setAttribute("infoSetEntidadFinancieraDiferido", entidadInfoSet);
                    	}else{
                			request.setAttribute("infoSetEntidadFinancieraDiferido", entidadInfoSet);
                    	}
                	}
            	}else{
            		request.setAttribute("resultInfo", new ResultInfo (true, null, "En estos momentos ninguna de las entidades financieras adscritas a la Plataforma Telem�tica de Pago admite el pago con NRC diferido. Disculpe las molestias."));
            		throw new Exception("Estamos en pago diferido y el combo de entidades financieras es nulo");
            	}
            }
            // vuelvo a cargar el combo de entidades financieras normales
            cargarDatosComboEntidadesFinancieras(request, userProfile);      
            
            //cargarDatosComboOFPresentacion(request, userProfile);
            ResultInfo resultInfo = getCombosOficinaPresentacion(request, userProfile, liquidacion);
            if (resultInfo.getError())
                throw new Exception();
            
            //GestionPermisos: Me trae la informaci�n relativa a ese documento
            consultaLiquidacionInfo = getLiquidacionBLJBean(request).getConsultaLiquidacion(userProfile,liquidacion);
             
            request.getSession().setAttribute("userProfile", userProfile); 
            request.getSession().setAttribute("liquidacionInfo", consultaLiquidacionInfo); 
            
            urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO);                                       
            
        } catch (Exception e) {
            throw new ApplicationError(new ResultInfo(true, "PDF005", 
                getSessionJBean(request).getLiteral("PDF005")), e);
        }
        return urlDst;
    } 
private String pagoPresentacion(HttpServletRequest request, UserProfileInfo userProfile){

    String sId = null;
    long idEvolucion;
    String urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_RESULTADO);
    LiquidacionInfo liquidacionInfo = null;
    PresentacionInfo presentacionInfo = null;
    String codigoTerritorial = null;
    ResultInfo errorEnProceso = null;
    request.setAttribute("pago",request.getParameter("pago"));
    sId = (String)request.getSession().getAttribute("idFirma"); 
    String esTarjeta = request.getParameter("esTarjeta");
    if(esTarjeta == null){
        esTarjeta = "";
    }
    String tarjeta = request.getParameter("tarjeta");
    String fecCadTarjeta = request.getParameter("fecCadTarjeta");
            
    //Borramos los parametros utilizados en la pagina anterior
    request.getSession().removeAttribute("codigoTerritorialOficinaPresentacion");
    request.getSession().removeAttribute("literalOficinaPresentacion");
    request.getSession().removeAttribute("literalOficinaPresentacionDocumento");
    request.getSession().removeAttribute("comboOficinaPresentacion");
    request.getSession().removeAttribute("codigosTerritorialesHash");
    request.getSession().removeAttribute("id_"+request.getSession().getAttribute("idFirma"));
    request.getSession().removeAttribute("idFirma");
    
    codigoTerritorial = request.getParameter("codigoTerritorial");
    idEvolucion=Long.parseLong(request.getParameter("idEvolucion"));
     
    try{       
        logger.trace("##########################################################################################");
        logger.trace("##########################################################################################");
        logger.trace("####################### PROCESO DE PAGO PRESENTACION SIMPLIFICADO ########################");
        logger.trace("##########################################################################################");
        logger.trace("##########################################################################################");
        
                                                
        /*****************************************/
        /** RECOGEMOS LOS PARAMETROS NECESARIOS **/
        /*****************************************/ 
        
        //Datos entidad financiera
        String nombreEntidadFinanciera = "";
        int codEntidad = 0;
        String CCC = "";
        String codigoEntidadObject = "";
        String codigoIban = "";
        String nrcDiferido = null;
        String fechaPagoDiferido = null;
        String idLiquidacion = request.getParameter("id");
        
        //TODO Fernando Piedra. Se comprueba antes de continuar si una liquidaci�n ya ha sido pagada por otros medios.
	    String modelo = idLiquidacion.substring(0, 3);
    	String version = idLiquidacion.substring(3,4);
    	String numeroDocumento = idLiquidacion.substring(4);
    	
    	//Antes de nada, obtengo el concepto del documento:
    	liquidacionInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
        String conceptoConsulta = null;
        if(liquidacionInfo != null){
            conceptoConsulta = Utiles.controlaConcepto(liquidacionInfo.getConcepto());
        }
    	
		boolean esPagada = getLiquidacionBLJBean(request).compruebaLiquidacionPagada(modelo,version,numeroDocumento,conceptoConsulta);
		if(esPagada){
			ResultInfo resultError = new ResultInfo();
			resultError.setError(true);
			resultError.setMessage("El documento que est� intentando pagar/presentar ya existe en el sistema. No es posible realizar su pago/presentaci�n telem�tico. Contacte con el servicio de atenci�n al usuario (CEIS).");
			request.setAttribute("resultInfo", resultError);
			return getUrl(Constantes.MOSTRAR_ERROR);
		}
                 
        liquidacionInfo.setJustificante(liquidacionInfo.getIdLiquidacion());                                                            
        presentacionInfo = new PresentacionInfo(liquidacionInfo); 
        presentacionInfo.setIdEvolucion(idEvolucion);
        boolean hayUrlRecibo = devolverUrlRecibo(request, userProfile, liquidacionInfo);
        boolean esPagoConcuenta = false;
        
        if (presentacionInfo.getTotalIngresar() > 0){
            // Para saber si es Pago diferido pero lo estoy pagando con cuenta                           
            boolean estaSeleccionadoNRCDif = (null != request.getParameter("esPagDif"))? Boolean.parseBoolean(request.getParameter("esPagDif")): false;
            String entidadFinancieraAux = null;
            if (estaSeleccionadoNRCDif){
                entidadFinancieraAux=request.getParameter("entidadFinancieraDif");
            }else{
                entidadFinancieraAux=request.getParameter("entidadFinanciera");
            }
            // Validamos el NRC
            if (estaSeleccionadoNRCDif){                
                logger.info("Proceso Simplificado pagoPresentacion: Validamos el NRC del modo de pago NRC diferido antes de continuar con el proceso de firma/pago/presentaci�n");
                String idRecuperado = request.getParameter("id");
                String documentoRecueprado = request.getParameter(Constantes.PDF_ID_AUTOLIQUIDACION);                
                String url = validaNRCDiferido(request, userProfile, idRecuperado, documentoRecueprado, liquidacionInfo);
                if (null != url && !"".equals(url)){
                    return url;
                }
            }
            
            if (null != entidadFinancieraAux){ 
                //Datos entidad financieran
                codigoEntidadObject = new String(entidadFinancieraAux);
                presentacionInfo.setPagoCuentaOTarjeta(!estaSeleccionadoNRCDif);
                if (!liquidacionInfo.isPagoDiferido() 
                        || (liquidacionInfo.isPagoDiferido() && !estaSeleccionadoNRCDif)
                        || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
                    codigoIban = request.getParameter("codigoIban")!=null?request.getParameter("codigoIban"):"";
                    logger.debug("codigoIban:"+codigoIban);
                }else{
                	nrcDiferido= request.getParameter("nrcDiferido");
                	fechaPagoDiferido = request.getParameter("fechaPagoDiferido");
                }
                
                if(userProfile.getIdTipoContrato()==Constantes.ID_TIPO_CONTRATO_FUNCIONARIO
                		|| userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO_R40)
                    nombreEntidadFinanciera = (String) request.getSession().getAttribute("nombreEntidadFinanciera");
                else
                    nombreEntidadFinanciera = request.getParameter("nombreEntidadFinanciera");
                
                
                //Guardamos los datos en el userProfile 
                userProfile.setCodEntidadObject(new Integer(codigoEntidadObject));
                if (esTarjeta != null &&!esTarjeta.equals("true")){
                    // Para saber si es Pago diferido pero lo estoy pagando con cuenta
                    esPagoConcuenta = !StringUtils.isEmpty(codigoIban);
                    if (!liquidacionInfo.isPagoDiferido() 
                	        || (liquidacionInfo.isPagoDiferido() && esPagoConcuenta)
                            || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
                		
                		userProfile.setCodigoIban(codigoIban);
                	}else if (estaSeleccionadoNRCDif){
                	    userProfile.setCodigoIban(null);
                	}
                    StringBuffer cccStringBuffer = new StringBuffer("");
                    if (!liquidacionInfo.isPagoDiferido() 
                            || (liquidacionInfo.isPagoDiferido() && esPagoConcuenta)
                            || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
                    	
                    	cccStringBuffer.append(Utiles.formateaIban(codigoIban));
                    }    
                    CCC =  cccStringBuffer.toString();
                }
                if (!liquidacionInfo.isPagoDiferido() 
                        || (liquidacionInfo.isPagoDiferido() && !estaSeleccionadoNRCDif)
                        || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
                    codEntidad = Integer.parseInt(request.getParameter("entidadFinanciera"));
                }else{
                    codEntidad = Integer.parseInt(request.getParameter("entidadFinancieraDif"));
                }
            }
            //Info log
            logger.trace("**********************************************************************************************");
            logger.trace("Recogiendo los parametros necesarios para iniciar el proceso de pago/presentacion SIMPLIFICADO");
            logger.trace("idFirma: " +sId);
            logger.trace("Codigo Territorial: " +codigoTerritorial);
            logger.trace("Nombre Entidad Financiera: " + nombreEntidadFinanciera);
            logger.trace("Codigo entidad financiera: " + codEntidad);
            logger.trace("CCC: " +CCC);
            logger.trace("N� Tarjeta: " +tarjeta);
            logger.trace("**********************************************************************************************");
        }
        /**************************/ 
        /**** PROCESO DE PAGO *****/    
        /**************************/
        logger.trace("*************************");
        logger.trace("COMIENZO DEL PROCESO PAGO");
        logger.trace("*************************");

            if (presentacionInfo.getTotalIngresar() > 0){
                presentacionInfo.setCodEntidadObject(new Integer(codigoEntidadObject));
                if (esTarjeta != null && esTarjeta.equals("true")){
                    presentacionInfo.setTarjetaObject(tarjeta);
                    SimpleDateFormat simpleDate = new SimpleDateFormat(Constantes.FORMATO_FECHA_CAD);
                    presentacionInfo.setFechaCadTarjetaObject(simpleDate.parse(fecCadTarjeta));
                    presentacionInfo.setCodigoIban(null);
                }else{
                	if (!liquidacionInfo.isPagoDiferido() 
                	        || (liquidacionInfo.isPagoDiferido() && esPagoConcuenta)
                            || userProfile.getIdTipoUsuario() == Constantes.ID_TIPO_FUNCIONARIO){
                	    presentacionInfo.setCodigoIban(codigoIban);
                	}else{
                		presentacionInfo.setNrc(nrcDiferido);
                    	SimpleDateFormat simpleDate = new SimpleDateFormat(Constantes.FORMATO_FECHA_CONSULTA);								 			
						presentacionInfo.setFechaPago(simpleDate.parse(fechaPagoDiferido));
						presentacionInfo.setCodigoIban(null);
                    }
                    presentacionInfo.setFechaCadTarjetaObject(null);
                    presentacionInfo.setTarjetaObject(null);
                }
            }
            presentacionInfo.setCodigoTerritorial(codigoTerritorial);
            presentacionInfo = pagarPresentar(request, userProfile, presentacionInfo);
            if (presentacionInfo.getResultInfo().getSubCode().equals(Constantes.GESTION_PAGO_ERROR)){
                request.getSession().setAttribute("resultInfo", presentacionInfo.getResultInfo());
                return getUrl(Constantes.MOSTRAR_ERROR);
            }
            if (presentacionInfo.getResultInfo().getError()){
                request.getSession().setAttribute("errorEnProceso", presentacionInfo.getResultInfo());
            } 
            request.getSession().setAttribute("presentacionInfo", presentacionInfo);
            
            logger.trace("##########################################################################################");
            logger.trace("##########################################################################################");
            logger.trace("################### FIN PROCESO DE PAGO PRESENTACION SIMPLIFICADO ########################");
            logger.trace("##########################################################################################");
            logger.trace("##########################################################################################");

            request.setAttribute("nombreEntidadFinanciera", nombreEntidadFinanciera);
            request.setAttribute("ccc", CCC);
            
            boolean estadoFinal = false;
            if(presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO 
                    || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
                    || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
                    || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_NRC)
            estadoFinal = true;     
//          Si hay URL de grabaci�n, reenviar los datos a dicha URL
            String aplicacion = getPresentacionBLJBean(request).obtenerNombreAplicacion(liquidacionInfo.getIdLiquidacion(), userProfile, estadoFinal);
            request.getSession().setAttribute("aplicacion", aplicacion);
            

            TipoEstadoLiquidacionInfo estadoLiquidacionInfo = getPresentacionBLJBean(request).getTipoEstadoLiquidacion(userProfile, presentacionInfo.getIdEstadoActual());
            String literalEstadoActual = getSessionJBean(request).getLiteral(estadoLiquidacionInfo.getLiteral());
            presentacionInfo.setLiteralEstadoActual(literalEstadoActual);
            //Si la peticion es externa
            boolean grabado = false;
            if(aplicacion != null && !aplicacion.equals("")){
            	// Si venimos desde GESTORIA actualizamos tambi�n el estado en la plataforma de gestoria 
                if (null !=  presentacionInfo.getNombreAplicacion() &&  presentacionInfo.getNombreAplicacion().equals("GESTORIA")){
                	try{
                		logger.debug ("Actualizamos el documento en la plataforma de Gestor�a");        		
                		String codErrorEnPago = (null != ((String)request.getAttribute("motivoError"))) ? (String)request.getAttribute("motivoError") : "";
                		if (presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_EN_PAGO && (null == codErrorEnPago || "".equals(codErrorEnPago))){
                			// Obtener c�digo del error que ten�a para coloc�rselo de nuevo
                			codErrorEnPago = getLiquidPreseBLJBean(request).obtenerCodigoErrorGestoria(presentacionInfo.getIdLiquidacion());
                		}
                		getLiquidPreseBLJBean(request).actualizarGestoria(userProfile, presentacionInfo.getIdLiquidacion(), presentacionInfo.getIdEstadoActual(), codErrorEnPago);
                	}catch (Exception ex){
                    	logger.error("Error al actualizar el estado del documento " + presentacionInfo.getIdLiquidacion() + " en la plataforma de gestor�as. Excepcion: " + ex);            		
                	} 
                }else{
	                String urlGrabacion = getPresentacionBLJBean(request).obtenerURLGrabacion(aplicacion, userProfile);
	                
	                if(urlGrabacion != null && !"".equals(urlGrabacion)){
	                    //Hacer la peticion de envio a dicha url
	                    CifradoInfo datosCifrado = getPresentacionBLJBean(request).obtenerDatosCifrado(aplicacion, userProfile);
	                    enviarUrlGrabacion(datosCifrado, urlGrabacion, presentacionInfo);
	                    //Envio de la confirmacion de grabacion
	                    presentacionInfo.setEntrega(1);
	                    try{
	                        boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
	                        if(!result){
	                            throw new Exception("Error en la actualizacion de idEntrega");
	                        }else{
	                            grabado = result;
	                        }
	                    }catch(Exception e){
	                        logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
	                        throw new Exception(e);
	                    }
	                }
                }
            }
            	
            //REDIRECCIONAMOS A LA PAGINA CON EL RESULTADO DEL PROCESO
            urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_FRAME_RESULTADO_SERVLET);
            if(hayUrlRecibo){
            	if (null != request.getSession().getAttribute("accesoPlages") && ("true").equals(request.getSession().getAttribute("accesoPlages"))){
            		request.setAttribute("userProfile",userProfile);    					
            		LiquidacionInfo liquidacionInfoConsulta = new LiquidacionInfo();					
					//Recojo la url de recibo de la tabla sn_aplica
					liquidacionInfoConsulta.setNombreAplicacion("GESTORIA");
					hayUrlRecibo = devolverUrlRecibo(request, userProfile, liquidacionInfoConsulta);
					if(hayUrlRecibo){
						//Como hay urlRecibo, redirecciono
						if(request.getAttribute("urlRecibo") != null){
							logger.debug("Redirecciono a la url: " + (String)request.getAttribute("urlRecibo"));
							request.setAttribute("aplicacion","GESTORIA");
							urlDst = getUrl(Constantes.URL_RECIBO_TARJETA_FRAME);
						}else{
							throw new Exception("No existe url de recibo");
						}
					}
            	}else{
	            	estadoFinal = false;
	                if(presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PAGADO 
	                    || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO 
	                    || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_PRESENTADO_PENDIENTE_ENTREGAR
	                    || presentacionInfo.getIdEstadoActual() == Constantes.ESTADO_ERROR_NRC)
	                        estadoFinal = true;
	                aplicacion = getPresentacionBLJBean(request).obtenerNombreAplicacion(presentacionInfo.getIdLiquidacion(), userProfile, estadoFinal);
	                //Creacion del String encriptado a partir del DocRespuesta que viene del jar de tasas
	                DocumentoRespuesta docRes = enviarUrlRecibo(request,true,presentacionInfo,presentacionInfo.getIdEstadoActual(),presentacionInfo.getReferencia());
	                String xmlEncriptado = obtenerDocResEncriptado(request,docRes,aplicacion);
	                if(xmlEncriptado != null){
	                    request.setAttribute("datosEncriptados", xmlEncriptado);
	                    urlDst = getUrl(Constantes.URL_RECIBO);
	                }else{
	                    logger.error("Ha habido un error al intentar encriptar el docRespuesta que debemos de enviar para la aplicacion: " + aplicacion);
	                    urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	                }   
	                //Envio de la confirmacion de recibo
	                if(grabado){
	                    presentacionInfo.setEntrega(2);
	                }else
	                    presentacionInfo.setEntrega(3);
	                try{
	                    boolean result = getLiquidacionBLJBean(request).actEntrega(userProfile,presentacionInfo);
	                    if(!result){
	                        throw new Exception("Error en la actualizacion de idEntrega");
	                    }
	                }catch(Exception e){
	                    logger.error("Error en la actualizacion del campo IdEntrega en sn_webliq: " + e.toString());
	                    throw new Exception(e);
	                }
            	}
           }


    } catch (Exception e) {
        logger.error("Error en el proceso de pago y presentacion simplificada: " +JFactoryException.getStackTrace(e));                      
        if (errorEnProceso == null)
            errorEnProceso = new ResultInfo(true, "PDF005", getSessionJBean(request).getLiteral("PDF005"));
        logger.trace("Guardamos en el request la informacion del error: " +errorEnProceso);             
        request.getSession().setAttribute("presentacionInfo", presentacionInfo);
        request.getSession().setAttribute("errorEnProceso", errorEnProceso);                        
    }
    return urlDst;
}

	/**
	 * M�todo que se invoca cuando se realiza una incorporaci�n SIMPLIFICADA y el cual elimina el documento incorporado
	 * y sale de la aplicaci�n cerrando la ventana del navegador
	 * @param request
	 * @param userProfile
	 * @return
	 */
	private String peticionSalir (HttpServletRequest request, UserProfileInfo userProfile){
		
		String urlDst = getUrl(Constantes.CERRAR_VENTANA);	
			    
	    try{	    	
	    	// Obtenemos el idLiquidacion de la liquidaci�n incorporada de forma simplificada
			String idLiquidacion = request.getParameter("idLiquidacionSalir");
			if (null == idLiquidacion || "".equals(idLiquidacion))
				throw new Exception("El n�mero de la liquidaci�n es nulo");
			
			// Obtenemos la liquidaci�n para ver el estado en el que se encuentra
			LiquidacionInfo liquid = getLiquidacionBLJBean(request).getLiquidacion(userProfile, idLiquidacion);
			if (null == liquid || (null != liquid && null != liquid.getResultInfo() && liquid.getResultInfo().getError())){
    			throw new Exception ("El idLiquidacion origen es nulo: " + liquid.getResultInfo().getMessage());
    		}
			
			// Comentado por lcf005 el 03.05.2016
			// No borrar el documento de la BD
			//if (null != liquid && (liquid.getIdEstadoActual() != Constantes.ESTADO_PENDIENTE_FIRMA && 
			//		liquid.getIdEstadoActual() != Constantes.ESTADO_PENDIENTE_PAGAR_PRESENTAR && 
			//		liquid.getIdEstadoActual() != Constantes.ESTADO_ERROR_EN_PAGO &&
			//		!(liquid.getIdEstadoActual() == Constantes.ESTADO_ERROR_EN_PRESENTACION && liquid.getTotalIngresar() == 0 )))
			//	throw new Exception("El documento " +  idLiquidacion + " no se encuentra en un estado apropiado para su eliminaci�n. Estado: " + liquid.getIdEstadoActual());
			// Primero, comprobamos si tiene adjuntos y, en caso afirmativo, los borramos
			//ResultInfo ri = getLiquidacionBLJBean(request).eliminarTodosDocumentosAdjuntos(idLiquidacion);
			//if (null != ri && ri.getError()){ 
		    //		throw new Exception("Ha ocurrido un error al intentar eliminar los documentos adjuntos de: " + idLiquidacion);
		    //}
			// Llamo al m�todo borrar f�sico y retorno a la p�gina de cierre de ventana
			// ResultInfo rs = getPresentacionBLJBean(request).deleteWebLiquidacionAndChildList(userProfile, idLiquidacion);
			// if (null != rs && rs.getError()){ 
			//	throw new Exception("Ha ocurrido un error al intentar eliminar el documento: " + idLiquidacion);
			//}
			// Fin comentado por lcf005 el 03.05.2016
			
			// si venimos de una aplicaci�n externa que tiene url de recibo, no cierro la ventana, mando el control a la aplicaci�n externa
			boolean hayUrlRecibo = devolverUrlRecibo(request, userProfile, liquid);
			if(hayUrlRecibo){		
	            logger.debug("Informando del error a la aplicacion externa: "+liquid.getNombreAplicacion());
	            // Miramos si venimos de plages
	            if (null != request.getSession().getAttribute("accesoPlages") && ("true").equals(request.getSession().getAttribute("accesoPlages"))){
            		request.setAttribute("userProfile",userProfile);    					
            		LiquidacionInfo liquidacionInfoConsulta = new LiquidacionInfo();					
					//Recojo la url de recibo de la tabla sn_aplica
					liquidacionInfoConsulta.setNombreAplicacion("GESTORIA");
					hayUrlRecibo = devolverUrlRecibo(request, userProfile, liquidacionInfoConsulta);
					if(hayUrlRecibo){
						//Como hay urlRecibo, redirecciono
						if(request.getAttribute("urlRecibo") != null){
							logger.debug("Redirecciono a la url: " + (String)request.getAttribute("urlRecibo"));
							request.setAttribute("aplicacion","GESTORIA");
							urlDst = getUrl(Constantes.URL_RECIBO_TARJETA_FRAME);
						}else{							
							logger.error("La url de recibo para la aplicaci�n GESTORIA , es nula o ha habido un error. Contin�o con el proceso cerrando la ventana del navegador.");
							urlDst = getUrl(Constantes.CERRAR_VENTANA);	
						}
					}
            	}else{	            
		            //Creacion del docRes
	                DocumentoRespuesta docRes = new DocumentoRespuesta();
	                docRes.setNumeroDocumento(idLiquidacion);
	                docRes.setEstado("ERROR");
	                docRes.setError("El proceso de pago/presentaci�n telem�tico ha sido cancelado por el usuario");
	                // Estado 4 indica que es un error general no tipificado y por tanto mostrar� el error que se pase
	                // en este caso el usuario ha cancelado el proceso de pago telem�tico pulsando el bot�n "Cancelar"
	                docRes.setCodigoEstadoPago(4);
	                docRes.setReferenciaExterna(liquid.getReferencia());
	                String xmlEncriptado = obtenerDocResEncriptado(request,docRes,liquid.getNombreAplicacion());
	                if(xmlEncriptado != null){
	                    request.setAttribute("datosEncriptados", xmlEncriptado);
	                    urlDst = getUrl(Constantes.URL_RECIBO);
	                }else{
	                    logger.error("Ha habido un error al intentar encriptar el docRespuesta que debemos de enviar para la aplicacion: " + liquid.getNombreAplicacion());
	                    urlDst = getUrl(Constantes.MOSTRAR_ERROR);
	                }
            	}				
           }else{           
               logger.error("La url de recibo para la aplicaci�n " + liquid.getNombreAplicacion() + " , es nula o ha habido un error. Contin�o con el proceso cerrando la ventana del navegador.");               
           }
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			request.setAttribute("resultInfo",new ResultInfo(true,"Ha ocurrido alg�n error en la cancelaci�n Telem�tica de Pago. No se ha realiado el Pago/Presentaci�n del documento",e.getMessage()));
			urlDst = getUrl(Constantes.MOSTRAR_ERROR);
		}
		return urlDst;
	}
	
	/**
	 * M�todo para eliminar adjuntos y continuar con la presentaci�n simplificada.
	 * @param request
	 * @param userProfile
	 * @return
	 * @throws Exception 
	 */
    private String continuarProcesoAdjuntosSimplificada(HttpServletRequest request, UserProfileInfo userProfile) throws Exception {
    	String urlDst = getUrl(Constantes.MOSTRAR_ERROR);    	
    	try{	    	  	
    		ArrayList<String> documentosFirmaMultiple = new ArrayList<String>();
    		String doc_origen = (String)request.getSession().getAttribute("doc_origen_adj");
    		if (null == doc_origen){
        		throw new Exception("Ha ocurrido un error al intentar obtener el id de la liquidaci�n origen.");
        	}else{
        		documentosFirmaMultiple.add(doc_origen);
        	}   
            request.getSession().setAttribute(Constantes.LISTA_NUMERO_DOCUMENTO_FIRMA_MULTIPLE, documentosFirmaMultiple);
            // Colocamos en el request de nuevo el id de firma del documento origen a los adjuntos
            String idFirma = (String)request.getSession().getAttribute("idFirmaOrigenAdjunto");
            if (null == idFirma ||"".equals(idFirma)){
            	throw new Exception ("El idFirma del documento origen a los adjuntos es nulo o vac�o");
            }
            request.setAttribute("idFirma", idFirma);
            request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, doc_origen);
    		request.getSession().removeAttribute("adjuntos_webliq");
    		
    		//Recupero el iban si existe
    		LiquidacionInfo liquid = getLiquidacionBLJBean(request).getLiquidacion(userProfile, doc_origen);
    		if(liquid != null && liquid.getCodigoIban() != null && !"".equals(liquid.getCodigoIban())){
    			request.setAttribute("codigoIban", liquid.getCodigoIban());
    		}
    		
    		if (userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO){
    			if("true".equals(userProfile.getAutorizacion().getTarjeta())){
	            	request.setAttribute("pagoTarjeta","pagoTarjeta");
	            }
    			//Valido si en los adjuntos firmados existe el modelo AU7. 
    			//Si es asi, lo guardar� en una variable en el request para su posterior tratamiento.
    			DocumentoAdjuntoInfoSet adjuntosObject = liquid.getAdjuntos();
    			
    			if(adjuntosObject != null && adjuntosObject.getArrayList() != null){
    			    List<DocumentoAdjuntoInfo> listaAdjuntos = adjuntosObject.getArrayList();
    			    for(DocumentoAdjuntoInfo docAdj:listaAdjuntos){
    			        if(Constantes.AUTORIZACION_AU7_VERSION.equals(docAdj.getCodmodel()+""+docAdj.getVersion())
    			                && docAdj.getIdFirmaDocumento() != null && docAdj.getIdFirmaDocumento() != -1){
    			            //Si se ha encontrado firmado un adjunto con nombre AU71.... se establece el parametro adjuntoAU71firmado.
    		                request.setAttribute(Constantes.ADJUNTO_AU7_FIRMADO, "true");
    			        }
    			    }
    			}
    			
    			urlDst = getUrl(Constantes.PRESENTACION_FUNCIONARIO_FRAME);
    		}else{
    			urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO);
    		}
    	}catch(Exception e){
    		logger.error(e.getMessage(), e);
    		urlDst = getUrl(Constantes.MOSTRAR_ERROR);
		}
    	return urlDst;
	}
    
    /**
     * M�todo para continuar la presentaci�n simplificada sin adjuntar documentaci�n
     * Le damos al bot�n "Continuar sin documentaci�n adjunta"
     * @param request
     * @param userProfile
     * @return
     * @throws Exception
     */
    private String continuarSimplificadaSinAdjuntar (HttpServletRequest request, UserProfileInfo userProfile) throws Exception {
    	String urlDst = getUrl (Constantes.MOSTRAR_ERROR);
    	try{
    		ValidacionRequest validaRequest = new ValidacionRequest(request);   
    		String docOrigen = (String)request.getSession().getAttribute("doc_origen_adj");    		 		
    		String idFirma = validaRequest.getParameter("idFirmaOrigenAdjunto");    		
    		LiquidacionInfo liquid = getLiquidacionBLJBean(request).getLiquidacion(userProfile, docOrigen);
    		
    		if (null == liquid || (null != liquid && null != liquid.getResultInfo() && liquid.getResultInfo().getError())){
    			throw new Exception ("El idLiquidacion origen es nulo: " + liquid.getResultInfo().getMessage());
    		}
    		if (liquid.getIdEstadoActual() != Constantes.ESTADO_PENDIENTE_FIRMA) {
    			throw new Exception ("El documento no est� en estado pendiente de firma");   			
    		}
    		
    		if (null == idFirma || "".equals(idFirma)){
    			idFirma = (String)request.getSession().getAttribute("idFirmaOrigenAdjunto");
    			if (null == idFirma || "".equals(idFirma)){
    				throw new Exception ("El id de firma del documento origen es nulo o vac�o");
    			}
    		} 
    		request.getSession().removeAttribute("adjuntos_webliq");
    		request.setAttribute("idFirma", idFirma);
    		if(liquid != null && liquid.getCodigoIban() != null && !"".equals(liquid.getCodigoIban())){
                request.setAttribute("codigoIban", liquid.getCodigoIban());
            }
    		// Para que podamos volver atr�s en m�s de una pantalla
    		request.getSession().setAttribute("idFirmaOrigenAdjunto", idFirma);    		
    		request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, docOrigen);
    		if (userProfile.getIdTipoContrato() == Constantes.ID_TIPO_CONTRATO_FUNCIONARIO){
    			if("true".equals(userProfile.getAutorizacion().getTarjeta())){
	            	request.setAttribute("pagoTarjeta","pagoTarjeta");
	            }
    			urlDst = getUrl(Constantes.PRESENTACION_FUNCIONARIO_FRAME);
    		}else{
    			urlDst = getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO);
    		}
    	}catch (Exception e){
    		logger.error(e.getMessage(), e);
    		urlDst = getUrl(Constantes.MOSTRAR_ERROR);
    	}
    	return urlDst;
    }
    
    /**
     * M�todo para adjuntar documentaci�n en la presentaci�n simplificada
     * Le damos al bot�n "Incorporar documentaci�n adjunta"
     * @param request
     * @param userProfile
     * @return
     * @throws Exception
     */
    private String continuarSimplificadaConAdjuntar (HttpServletRequest request, UserProfileInfo userProfile) throws Exception {
    	String urlDst = getUrl (Constantes.MOSTRAR_ERROR);
    	try{
    		ValidacionRequest validaRequest = new ValidacionRequest(request);    		
    		String idFirma = validaRequest.getParameter("idFirmaOrigenAdjunto");
    		String docOrigen = (String)request.getSession().getAttribute("doc_origen_adj");
    		if (null == idFirma || "".equals(idFirma)){
    			idFirma = (String)request.getSession().getAttribute("idFirmaOrigenAdjunto");
    			if (null == idFirma || "".equals(idFirma)){
    				throw new Exception ("El id de firma del documento origen es nulo o vac�o");
    			}
    		}
    		request.getSession().setAttribute("idFirmaOrigenAdjunto", idFirma);
    		// Comprobamos que el estado de la liquidacion Origen sea pendente de firma
    		LiquidacionInfo liquid = getLiquidacionBLJBean(request).getLiquidacion(userProfile, docOrigen);
    		if (null == liquid || (null != liquid && null != liquid.getResultInfo() && liquid.getResultInfo().getError())){
    			throw new Exception ("El idLiquidacion origen es nulo: " + liquid.getResultInfo().getMessage());
    		}
    		
    		if (null != liquid && liquid.getIdEstadoActual() == Constantes.ESTADO_PENDIENTE_FIRMA){
	    		// Comprobamos que el listado de documentos adjuntos a adjuntar no sea nulo
	    		DocumentoAdjuntoInfoSet adjuntos_modadj = (DocumentoAdjuntoInfoSet)request.getSession().getAttribute("adjuntos_modadj");
	    		if (null == adjuntos_modadj){
	    			throw new Exception ("El listado de documentos adjuntos obtenidos de sesi�n es nulo");
	    		}
	    		// Vemos si ya tiene adjuntado alg�n documento
	    		DocumentoAdjuntoInfoSet adjuntos_webliq = (DocumentoAdjuntoInfoSet)request.getSession().getAttribute("adjuntos_webliq");
	    		// Eliminamos si hab�a alg�n adjunto webliq en sesion y colocamos el nuevo
	    		request.getSession().removeAttribute("adjuntos_webliq");
	    		// Si no existe en sesi�n lo obtengo de BD
	    		//if (null == adjuntos_webliq){
	    			adjuntos_webliq = getLiquidacionBLJBean(request).getDocAdjuntosWebliq(docOrigen);
	    		//}
	    		if (null != adjuntos_webliq){    			
	        		request.getSession().setAttribute("adjuntos_webliq", adjuntos_webliq);
	        		// Vemos si ya est�n todos correctos y lo guardo en sesi�n
	            	boolean estanCorrectos = getLiquidacionBLJBean(request).todosDocumentosAdjuntosCorrectos(adjuntos_modadj, adjuntos_webliq);
	            	request.getSession().setAttribute("adjuntosCorrectos", String.valueOf(estanCorrectos));
	        	}
	    		// Indico que estoy adjuntando ficheros
	    		request.getSession().setAttribute("documento_adjunto", "true");
	    		urlDst = getUrl(Constantes.ADJUNTAR_DOCUMENTACION);
    		}else{
    			request.setAttribute("resultInfo", new ResultInfo(true, null, "S�lo se permite adjuntar documentaci�n a documentos en estado Pendiente de Firma."));
    			throw new Exception ("S�lo se permite adjuntar documentaci�n a documentos en estado Pendiente de Firma.");
    		}
    	}catch (Exception e){
    		logger.error(e.getMessage(), e);
    		urlDst = getUrl(Constantes.MOSTRAR_ERROR);
    	}
    	return urlDst;
    }
    
    /**
     * M�todo que redirige a la p�gina para adjuntar documentaci�n donde podemos elegir
     * el tipo de documentaci�n, e insertar el fichero y un comentario
     * @param request
     * @param userProfile
     * @return
     * @throws Exception
     */
    private String adjuntarDocumentacionSimplificada (HttpServletRequest request, UserProfileInfo userProfile) throws Exception {
    	String urlDst = getUrl (Constantes.MOSTRAR_ERROR);
    	try{    		
    		// Validamos los par�metros que recogemos
    		ValidacionRequest validaRequest = new ValidacionRequest(request);
    		String leyenda = validaRequest.getParameter("leyenda");
    		String modelo_adjuntado = validaRequest.getParameter("modeloAdj");    		
    		String extensionesPermitidas = validaRequest.getParameter("extensionesPermitidas");
    		String concepto = validaRequest.getParameter("conceptoAdjunto"); 
    		String validaFichero = validaRequest.getParameter("validaFichero");
    		String requerido = validaRequest.getParameter("requerido");
    		
    		if (null == leyenda || null == modelo_adjuntado || null == extensionesPermitidas || null == concepto){
    			throw new Exception ("Error: La leyenda, el modelo, concepto o la/s extensi�n/es del documento a adjuntar son nulos");
    		}    		
    		// Colocamos en el request que estamos en simplificada
    		request.setAttribute("simplificada","true");
    		request.setAttribute("leyenda",leyenda);
    		request.setAttribute("modeloAdj",modelo_adjuntado);    		
    		request.setAttribute("extensionesPermitidas",extensionesPermitidas);
    		request.setAttribute("conceptoAdjunto",concepto);
    		request.setAttribute("validaFichero",validaFichero);
    		request.setAttribute("requerido",requerido);
    		
    		// Recogemos el modelo Origen y comprobamos que no adjuntemos m�s del l�mite de documentos, que es 10 
    		String numeroDoc = (String)request.getSession().getAttribute("doc_origen_adj");
    		LiquidacionInfo liquidInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, numeroDoc);
    		if (null == liquidInfo || (null != liquidInfo && null != liquidInfo.getResultInfo() && liquidInfo.getResultInfo().getError())){
    			throw new Exception ("El idLiquidacion origen es nulo: " + liquidInfo.getResultInfo().getMessage());
    		}
    		
//    		Comentamos la linea que permite hacer el filtro que permite adjuntar solo 10 documentos.
    		
//    		if (null != liquidInfo && liquidInfo.getNumeroAdjuntos() == Constantes.LIMITE_NUMERO_ADJUNTOS){
//    			ResultInfo resultInfo = new ResultInfo();
//    			resultInfo.setError(true);
//				resultInfo.setMessage("N�mero l�mite de documentos adjuntos excedido. S�lo se permite adjuntar un m�ximo de " + Constantes.LIMITE_NUMERO_ADJUNTOS + " documentos adjuntos por documento.");
//				request.setAttribute ("resultInfo", resultInfo);			
//				request.setAttribute ("destino", Utiles.getPathSiriAction() + "PresentacionSimplificada?" + BaseServletController.OPERATION + "=" + Constantes.CONTINUAR_CON_ADJUNTAR);
//				throw new Exception("El documento origen: " + liquidInfo.getIdLiquidacion() + " ya tiene adjuntados el m�ximo n�mero permitido de documentos adjuntos, que es "+ Constantes.LIMITE_NUMERO_ADJUNTOS);
//			}
    		
    		urlDst = getUrl(Constantes.MOSTRAR_ADJUNTAR_DOCUMENTACION);
    	}catch (Exception e){
    		logger.error(e.getMessage(), e);
    		urlDst = getUrl(Constantes.MOSTRAR_ERROR);
    	}    	
    	return urlDst;    	
    }
    /**
     * Valida que el NRC es correcto, y redirige a la pantalla oportuna:
     * Si es correcto continua el proceso
     * Si no es correcto 
     * @param userProfile
     * @return
     */
    public String validaNRCDiferido (HttpServletRequest request, UserProfileInfo userProfile, String id, String idDocumentoFirmar, LiquidacionInfo liquid){
        // colocamos tambi�n los datos anteriores del pago Diferido: banco, cuenta, NRC y fecha de pago
        if (calcularNRCProcesoDiferido(request, userProfile, liquid.getTotalIngresar(), liquid.getNifSujeto(), liquid.getIdLiquidacion())){
            return null;
        }else{
            // eliminamos el indicador de bloqueo para que en la redirecci�n a la p�gina anterior se pueda volver a pulsar el bot�n "Siguiente"
            request.getSession().removeAttribute("pagoBloqueo");
            // tomamos los datos para volver a la pantalla anterior de simplificada
            request.setAttribute("idFirma", id);
            request.setAttribute(Constantes.PDF_ID_AUTOLIQUIDACION, idDocumentoFirmar);
            // datos para autocompletar los datos del NRC diferido
            request.getSession().setAttribute("entidaCombodDif", request.getParameter("entidaCombodDif"));
            request.getSession().setAttribute("nrcDiferido", request.getParameter("nrcDiferido"));
            request.getSession().setAttribute("fechaPagoDiferido", request.getParameter("fechaPagoDiferido"));  
            request.getSession().setAttribute("nombreEntidadFinanciera", request.getParameter("nombreEntidadFinanciera"));
            request.getSession().setAttribute("codEntidadFinanciera", request.getParameter("codEntidadFinanciera"));
            request.getSession().setAttribute("mensjeValidacionNRCDiferido", request.getAttribute("mensjeValidacionNRCDiferido"));
            logger.info((String)(request.getParameter("entidaCombodDif")) + " - " + request.getParameter("nombreEntidadFinanciera") + " - " + Integer.parseInt(request.getParameter("codEntidadFinanciera")));
            
            return getUrl(Constantes.PRESENTACION_SIMPLIFICADA_INICIO); 
        }
        
    }
    
    /**
	 * Valida que el NRC es correcto para presentacion por EP, y redirige a la pantalla oportuna: Si es
	 * correcto continua el proceso.
	 * 
	 * @param userProfile
	 * @return
	 */
    public String validaNRCDiferidoEP(HttpServletRequest request, UserProfileInfo userProfile, PresentacionInfo presentacion) {
		// colocamos tambi�n los datos anteriores del pago Diferido: banco, cuenta, NRC
		// y fecha de pago
		if (calcularNRCProcesoDiferido(request, userProfile, presentacion.getTotalIngresar(), presentacion.getNifSujeto(), presentacion.getIdLiquidacion())) {
			return null;
		} else {
			// eliminamos el indicador de bloqueo para que en la redirecci�n a la p�gina
			// anterior se pueda volver a pulsar el bot�n "Siguiente"
			request.getSession().removeAttribute("pagoBloqueo");
			
			// datos para autocompletar los datos del NRC diferido
			request.getSession().setAttribute("entidaCombodDif", request.getParameter("entidaCombodDif"));
			request.getSession().setAttribute("nrcDiferido", request.getParameter("nrcDiferido"));
			request.getSession().setAttribute("fechaPagoDiferido", request.getParameter("fechaPagoDiferido"));
			request.getSession().setAttribute("nombreEntidadFinanciera",request.getParameter("nombreEntidadFinanciera"));
			request.getSession().setAttribute("codEntidadFinanciera", request.getParameter("codEntidadFinanciera"));

			request.getSession().setAttribute("nrcValido", "false");
			
			return getUrl(Constantes.EXPEDIR_AUTORIZACION);
		}

	}
    
    /**
     * Actualizar adjunto validacion.
     * 
     * @param request the request
     * @param userProfile the user profile
     */
    public void actualizarAdjuntoValidacion(HttpServletRequest request, UserProfileInfo userProfile, long estado, String docAdjunto){
    	try{
	    	LiquidacionInfo liquidInfo = getLiquidacionBLJBean(request).getLiquidacion(userProfile, docAdjunto);
	    	if(liquidInfo != null && liquidInfo.getIdEstadoActual() != Constantes.ESTADO_DOCUMENTO_ADJUNTO){
	    		//Actualizar� su estado
	    		ResultInfo resultado = getLiquidacionBLJBean(request).setEstadoDocumentoAdjunto(userProfile, estado, docAdjunto);
	    		if(resultado != null && !resultado.getError()){
	    			logger.debug("Documento adjunto actualizado correctamente: " + docAdjunto + " al estado: " + estado);
	    		}else{
	    			logger.error("Documento adjunto no actualizado: " + docAdjunto + " al estado: " + estado);
	    			logger.error("Error: " + resultado.getMessage());
	    		}
	    	}else if (liquidInfo.getIdEstadoActual() != Constantes.ESTADO_DOCUMENTO_ADJUNTO){
	    		logger.debug("El documento est� ya valido o con errores: " + docAdjunto+ " , estado: " + liquidInfo.getIdEstadoActual());
	    	}else{
	    		logger.debug("El documento est� ya valido o con errores: " + docAdjunto);
	    	}
    	}catch(Exception e){
    		logger.error("Error al actualizar el documento : " + e.getMessage());
    	}	
    }
    
	/**
	 * smc017 Extrae la informacion autorizacion para tratarla.
	 */
	public AutorizacionInfo obtenerAutorizacion(String numeroDocumento, Long idUsuario, HttpServletRequest request)
			throws Exception {
		UserProfileInfo userProfile = new UserProfileInfo();
		userProfile.setIdUsuario(idUsuario);

		PresentacionInfo presentacionInfo = new PresentacionInfo();
		presentacionInfo.setIdLiquidacion(numeroDocumento);

		return getLiquidacionBLJBean(request).obtenerAutorizacion(userProfile, presentacionInfo);
	}
	
	public String calculaNrcFicticio(HttpServletRequest request, UserProfileInfo userProfile, String nifSujetoPasivo) {  
		String nrcGenerado = "";
		ConsultaLiquidacionInfo consultaLiquidacion = (ConsultaLiquidacionInfo) request.getSession().getAttribute("liquidacionInfo");
		
		String codliqui = consultaLiquidacion.getIdLiquidacion()!=null?consultaLiquidacion.getIdLiquidacion():"";
		String nifsujet = nifSujetoPasivo!=null?nifSujetoPasivo:"";
		String imptotin = String.valueOf(consultaLiquidacion.getTotalIngresar());
		imptotin = imptotin.replace(',', '.');
		String codentfi = String.valueOf(userProfile.getAutorizacion().getCodEntidad());
		String fecprese = request.getParameter("fechaPagoJustificanteRecibido")!=null?request.getParameter("fechaPagoJustificanteRecibido"):"";
		SimpleDateFormat sdf = new SimpleDateFormat ("dd/MM/yyyy");
		
		
		Double imptotinDouble = "".equals(imptotin)?0.0:Double.parseDouble(imptotin);
		try {
			Date fecpreseDate = sdf.parse(fecprese);
		
			int codentfiInt ="".equals(codentfi)?0000:Integer.parseInt(codentfi);  
	
			//Calculamos el digito de control que le corresponde
	
			PortableContext pc = PropApplication.getContext();
			if(!fecprese.contains(":")){
			    fecprese = fecprese + " 00:00:00";
	        }				
			// Obtenemos los datos para pasar a la librer�a DE nrc
			EntidadFinancieraInfo entidadFinancieraInfo = getPresentacionBLJBean(request).getEntidadFinanciera(userProfile, codentfiInt,fecprese);
			
			byte[] claveCifrado = CryptoBLImpl.doubleHash(pc,jfactory.sirija.admin.util.Utiles.getPropiedadesSiri().getString(Constantes.sirija_VECTOR));
			String listaCorrespondencia = entidadFinancieraInfo.getTablaConversion();
			String listaCorrespondenciaDes = CryptoBLImpl.decryptCredential(pc, claveCifrado, listaCorrespondencia);
			//justificanteConDigitoControl<-- Justificante + Caracter de Control
			String justificanteConDigitoControl = codliqui + 
					Presentacion.getCaracterControlJustificante(codliqui, listaCorrespondenciaDes, entidadFinancieraInfo.getCocienteCarControl());
			
			String cadenaNRC = Presentacion.getStringNRC(justificanteConDigitoControl, nifsujet, imptotinDouble, fecpreseDate, codentfi,entidadFinancieraInfo.getCodigoBanco());
			boolean nrcAvailable = jfactory.sirija.util.Utiles.isAvailable("jfactory.nrc.Nrc", logger);		
			if (nrcAvailable){
				nrcGenerado = jfactory.nrc.Nrc.getNRC(entidadFinancieraInfo.getCodigoBanco(), cadenaNRC);
			}else{
				logger.error("NO PUEDO GENERAR NRC. LIBRERIAS NRC NO ESTAN EN EL CLASSPATH. Lanzo excepcion");
				throw new Exception("NRC no disponible. No puedo generarlo");		
			}
		} catch (ParseException e) {
			logger.error("Error parseando la fecha "+fecprese);
		} catch (Exception e) {
			logger.error("Error calculando el NRC ficticio para la entidad 9999 "+e);
		}
		
		return nrcGenerado;
	}


}
