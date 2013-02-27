package com.atolcd.rfp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IParameterProvider;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.api.engine.ISolutionFile;
import org.pentaho.platform.api.repository.IContentItem;
import org.pentaho.platform.api.repository.ISolutionRepository;
import org.pentaho.platform.engine.core.solution.ActionInfo;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.services.solution.BaseContentGenerator;

import com.atolcd.rfp.utils.Templater;

/**
 * Content generator handles different functions :
 * - POST_FORM : Shows the upload form with templated texts.
 * - UPDATE_FORM : Handles file title and description, also moves tmp file to final destination.
 * - CHECK_PARAMS : Checks file size.
 * - GET_TREE : Generates a json string object that represents folder hierarchy.
 */
public class RFPContentGenerator extends BaseContentGenerator {

  private static final Log logger = LogFactory.getLog(RFPContentGenerator.class);

  private static final String MIME_HTML = "text/html";
  private static final String POST_FORM = "formulaire";
  private static final String UPDATE_FORM = "update";
  private static final String CHECK_PARAMS = "check";
  private static final String GET_TREE = "jsonTree";
  private static final String TMP_FOLDER = "tmp_folder";
  private static final String SOLUTION_FOLDER = "solution_folder";
  private static final String INLINE_STYLE = "style_retour";
  private static final String DEFAULT_INLINE_STYLE = "color:white;";

  /**
   * Encondig used for reading parameters.
   */
  public static String ENCODING = "UTF-8";

  @Override
  public void createContent() throws Exception {

    IPluginResourceLoader resLoader = PentahoSystem.get(IPluginResourceLoader.class, null);
    InputStream in = resLoader.getResourceAsStream(RFPContentGenerator.class, "config/RFP.properties");
    Properties config = new Properties();

    try {
      config.load(in);
    } catch (IOException e) {
      logger.error("Error while loading properties file", e);
    }

    final OutputStream out;
    final IContentItem contentItem;

    contentItem = this.outputHandler.getOutputContentItem("response", "content", "", this.instanceId, MIME_HTML);
    out = contentItem.getOutputStream(null);

    final IParameterProvider requestParams = this.parameterProviders.get(IParameterProvider.SCOPE_REQUEST);

    final String mode = requestParams.getStringParameter("mode", ""); //$NON-NLS-1$

    if (mode.equals(CHECK_PARAMS)) {
      // check size
      String response = "OK";

      Double sizeTocheck =
        Double.parseDouble(URLDecoder.decode(requestParams.getStringParameter("size", "-1"), ENCODING));

      String filename = URLDecoder.decode(requestParams.getStringParameter("filename", ""), ENCODING);

      int indexPoint = filename.lastIndexOf(".");
      if (indexPoint > 0 && indexPoint < filename.length() - 1) {

        String extension = filename.substring(indexPoint + 1).toLowerCase();
        Double maxSize = Double.parseDouble(config.getProperty("max_size"));

        if (sizeTocheck > maxSize) {
          response = "ERROR : " + config.getProperty("max_size_exceded");
        }

        ArrayList<String> allowedExts =
          new ArrayList<String>(Arrays.asList(config.getProperty("allowed_extensions").split(",")));
        if (extension == "" || !allowedExts.contains(extension)) {
          response = "ERROR : " + config.getProperty("extension_not_allowed");
        }
      } else {
        response = "ERROR : " + config.getProperty("extension_not_allowed");
      }

      out.write(response.getBytes());
      out.flush();
      return;
    }

    if (mode.equals(POST_FORM)) {
      String template = resLoader.getResourceAsString(RFPContentGenerator.class, "resources/html/form.html");
      String changed = Templater.substitute(template, config);
      out.write(changed.getBytes());
      out.flush();
      return;
    }

    final ISolutionRepository repository = PentahoSystem.get(ISolutionRepository.class, this.userSession);

    if (mode.equals(UPDATE_FORM)) {

      final String fname = URLDecoder.decode(requestParams.getStringParameter("fileName", ""), ENCODING);
      final String fileOnServer = URLDecoder.decode(requestParams.getStringParameter("fileOnServer", ""), ENCODING);
      final String fpath = URLDecoder.decode(requestParams.getStringParameter("path", ""), ENCODING);
      final String fdesc = URLDecoder.decode(requestParams.getStringParameter("desc", "Default description"), ENCODING);
      final String ftitre = URLDecoder.decode(requestParams.getStringParameter("titre", "Default title"), ENCODING);

      String tmpFolder = config.getProperty(TMP_FOLDER, "/tmp");
      String solutionFolder = config.getProperty(SOLUTION_FOLDER, "");

      File toPublish = new File(solutionFolder + File.separator + tmpFolder + File.separator + fileOnServer);
      FileInputStream fis = new FileInputStream(toPublish);
      byte[] b = new byte[fis.available()];
      fis.read(b);
      fis.close();

      // get instance of RFP
      RepositoryFilePublisher rfp = new RepositoryFilePublisher();
      // publish file

      String publishStatus = rfp.publishFile(fpath, fname, b, ftitre, fdesc, false);
      String messageStyle = config.getProperty(INLINE_STYLE, DEFAULT_INLINE_STYLE);
      String finalMessage = "<span style=\"" + messageStyle + "\">" + publishStatus + "</span>";
      out.write(finalMessage.getBytes());
      out.flush();

      toPublish.delete();

      return;
    }

    if (mode.equals(GET_TREE)) {
      // repository
      ISolutionFile baseFolder = repository.getRootFolder(ISolutionRepository.ACTION_EXECUTE);
      String jsonTree = generateSubTree(baseFolder, "", repository);// format : {nodeName:'name', childNodes:[]}
      out.write(jsonTree.getBytes(ENCODING));
      return;
    }

    final String solution = requestParams.getStringParameter("solution", ""); //$NON-NLS-1$
    final String path = requestParams.getStringParameter("path", ""); //$NON-NLS-1$
    final String action = requestParams.getStringParameter("action", ""); //$NON-NLS-1$

    final String fullPath = ActionInfo.buildSolutionPath(solution, path, action);

    ISolutionFile fileToGet = repository.getSolutionFile(fullPath, ISolutionRepository.ACTION_EXECUTE);

    if (fileToGet == null) {
      logger.error("Access Denied");
      out.write("Access Denied".getBytes(ENCODING));
      return;
    }

    setResponseHeaders(config.getProperty("filetype" + fileToGet.getExtension()), 0, fileToGet.getFileName());

    out.write(fileToGet.getData());
  }

  @Override
  public Log getLogger() {
    return logger;
  }

  private void setResponseHeaders(final String mimeType, final int cacheDuration, final String attachmentName) {
    // Make sure we have the correct mime type
    final HttpServletResponse response =
      (HttpServletResponse) this.parameterProviders.get("path").getParameter("httpresponse");
    response.setHeader("Content-Type", mimeType);

    if (attachmentName != null) {
      response.setHeader("content-disposition", "attachment; filename=\"" + attachmentName + "\"");
    }

    // Cache?
    if (cacheDuration > 0) {
      response.setHeader("Cache-Control", "max-age=" + cacheDuration);
    } else {
      response.setHeader("Cache-Control", "max-age=0, no-store");
    }
  }

  private String generateSubTree(ISolutionFile currentFolder, String currPath, ISolutionRepository repository) {
    String subTree = "";

    if (currentFolder == null) {
      return "";
    }

    if (currentFolder.isDirectory()) {
      subTree += "{";
      subTree += "\"nodeName\":\"" + currentFolder.getFileName() + "\"";
      subTree += ",\"nodePath\":\"" + currPath + "\"";
      ISolutionFile[] childs = currentFolder.listFiles();
      if (childs.length > 0) {
        subTree += ",\"childNodes\":[";
        // for each child add childs
        for (int y = 0; y < childs.length; y++) {
          if (childs[y].isDirectory()) {
            ISolutionFile childItem =
              repository.getSolutionFile(childs[y].getFullPath(), ISolutionRepository.ACTION_EXECUTE);
            subTree += generateSubTree(childItem, currPath + File.separator + childs[y].getFileName(), repository);
            subTree += (y == childs.length - 1 || subTree.endsWith("[")) ? "" : ",";
          }
        }
        subTree = (subTree.endsWith(",")) ? subTree.substring(0, subTree.length() - 1) : subTree;
        subTree += "]";
      }
      subTree += "}";
    }

    return subTree;
  }

}
