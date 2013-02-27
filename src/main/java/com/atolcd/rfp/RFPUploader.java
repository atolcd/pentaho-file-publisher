package com.atolcd.rfp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.web.servlet.IUploadFileServletPlugin;

/**
 * RFPUploader servlet for uploading files implementing IUploadFileServletPlugin.
 */
public class RFPUploader implements IUploadFileServletPlugin {

  private static final Log logger = LogFactory.getLog(RFPUploader.class);

  private static final String FILE_EXT = "tmp_ext";
  private static final String TMP_FOLDER = "tmp_folder";
  private static final String FOLDER_MAX_SIZE = "folder_max_size";
  private static final String FILE_MAX_SIZE = "max_size";

  Properties messages;

  /**
   * Uploader constructor.
   */
  public RFPUploader() {
    IPluginResourceLoader resLoader = PentahoSystem.get(IPluginResourceLoader.class, null);
    InputStream in = resLoader.getResourceAsStream(RepositoryFilePublisher.class, "config/RFP.properties");
    this.messages = new Properties();

    try {
      this.messages.load(in);
    } catch (IOException e) {
      logger.error("Error while loading propeties file", e);
    }
  }

  @Override
  public String getFileExtension() {
    return this.messages.getProperty(FILE_EXT, ".xml");
  }

  @Override
  public long getMaxFileSize() {
    long size = 3000000;

    try {
      size = Long.parseLong(this.messages.getProperty(FILE_MAX_SIZE, "3000000"));
    } catch (Exception e) {
      logger.error("Error parsing  max file size", e);
    }

    return size;
  }

  @Override
  public long getMaxFolderSize() {
    long size = 10000000;

    try {
      size = Long.parseLong(this.messages.getProperty(FOLDER_MAX_SIZE, "10000000"));
    } catch (Exception e) {
      logger.error("Error parsing max folder size", e);
    }

    return size;
  }

  @Override
  public String getTargetFolder() {
    return this.messages.getProperty(TMP_FOLDER, "/tmp");
  }

  @Override
  public void onSuccess(String arg0, HttpServletResponse response) {

    try {

      String littlepath = arg0.substring(arg0.lastIndexOf(File.separator) + 1);
      // print next page
      response.setContentType("text/html");

      response.getWriter().print("<script>window.parent.continueLoad('" + littlepath + "')</script>");
      response.setStatus(HttpServletResponse.SC_OK);

    } catch (IOException e1) {
      logger.error("Writer error in serlvet", e1);
    }

    // return the result to the client
    try {
      response.flushBuffer();
    } catch (IOException e) {
      logger.error("Error in serlvet", e);
    }

  }

}
