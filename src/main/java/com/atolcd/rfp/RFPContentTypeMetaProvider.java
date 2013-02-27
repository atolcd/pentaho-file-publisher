package com.atolcd.rfp;

import java.io.InputStream;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IFileInfo;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.ISolutionFile;
import org.pentaho.platform.api.engine.SolutionFileMetaAdapter;
import org.pentaho.platform.api.repository.ISolutionRepository;
import org.pentaho.platform.engine.core.solution.FileInfo;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * RFPContentTypeMetaProvider provides meta informations for files uploaded by RFP plugin.
 */
public class RFPContentTypeMetaProvider extends SolutionFileMetaAdapter {

  private static final Log logger = LogFactory.getLog(RFPContentTypeMetaProvider.class);

  @Override
  public IFileInfo getFileInfo(ISolutionFile solutionFile, InputStream in) {
    Document doc = null;
    try {

      IPentahoSession session = PentahoSessionHolder.getSession();

      if (session == null) {
        return null;
      }

      ISolutionRepository repository = PentahoSystem.get(ISolutionRepository.class, session);

      String title = solutionFile.getFileName();
      if (title != null && title.lastIndexOf(".") != -1) {
        title = title.substring(0, title.lastIndexOf("."));
      }

      String file = solutionFile.getFullPath().substring(0, solutionFile.getFullPath().lastIndexOf("."));

      ISolutionFile infoFile = repository.getSolutionFile(file + ".rfp", ISolutionRepository.ACTION_EXECUTE);

      if (infoFile == null) {
        return null;
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      doc = builder.parse(new InputSource(new StringReader(new String(infoFile.getData()))));

      XPath xpath = XPathFactory.newInstance().newXPath();
      //String author = (String) xpath.evaluate("/rfp/author", doc, XPathConstants.STRING); //$NON-NLS-1$
      String description = (String) xpath.evaluate("/rfp/description", doc, XPathConstants.STRING); //$NON-NLS-1$
      //String icon = (String) xpath.evaluate("/rfp/icon", doc, XPathConstants.STRING); //$NON-NLS-1$
      String titleEff = (String) xpath.evaluate("/rfp/title", doc, XPathConstants.STRING); //$NON-NLS-1$

      IFileInfo info = new FileInfo();
      info.setAuthor("");
      info.setDescription(description);
      // info.setIcon(PluginConfig.ICON);
      info.setTitle(titleEff);
      return info;

    } catch (Exception e) {
      logger.error("Error while loading file description", e);
    }
    return null;
  }
}
