package com.atolcd.rfp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.api.engine.IPluginResourceLoader;
import org.pentaho.platform.api.engine.ISolutionFile;
import org.pentaho.platform.api.engine.PentahoAccessControlException;
import org.pentaho.platform.api.repository.ISolutionRepository;
import org.pentaho.platform.engine.core.solution.ActionInfo;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.engine.core.system.PentahoSystem;

/**
 * RepositoryFilePublisher provides public method for being used as WebService by ws-content pentaho system.
 */
public class RepositoryFilePublisher {

  private static final Log logger = LogFactory.getLog(RepositoryFilePublisher.class);

  private static final String WRONG_FOLDER_PERMISSIONS = "permission_error_message";
  private static final String BAD_CREDENTIALS = "credentials_error_message";
  private static final String ALREADY_EXISTS_FILE = "file_exists_error_message";
  private static final String FOLDER_NOT_FOUND = "folder_error_message";
  private static final String FILE_CREATED = "file_created_message";
  private static final String MAX_SIZE_EXCEDED = "max_size_exceded";
  private static final String EMPTY_FILE = "empty_file";
  private static final String EMPTY_FILENAME = "empty_filename";
  private static final String MAX_SIZE = "max_size";

  /**
   * Default constructor
   */
  public RepositoryFilePublisher() {
  }

  /**
   * Public method used as a webservice.
   * 
   * @param fileName Final name for published file in repository
   */
  public String publishFile(final String path, String fileName, byte[] fc, String title, String desc, boolean overwrite) {

    IPluginResourceLoader resLoader = PentahoSystem.get(IPluginResourceLoader.class, null);
    InputStream in = resLoader.getResourceAsStream(RepositoryFilePublisher.class, "config/RFP.properties");
    Properties messages = new Properties();

    logger.info("Incoming file upload");

    try {
      messages.load(in);
    } catch (IOException e) {
      logger.error("Error while loading properties file", e);
    }

    int maxSixe = Integer.parseInt(messages.getProperty(MAX_SIZE));

    if (fc.length == 0) {
      logger.info(EMPTY_FILE);
      return messages.getProperty(EMPTY_FILE);
    }

    if ("".equals(fileName)) {
      logger.info(EMPTY_FILENAME);
      return messages.getProperty(EMPTY_FILENAME);
    }

    if (fc.length > maxSixe) {
      logger.info(MAX_SIZE_EXCEDED);
      return messages.getProperty(MAX_SIZE_EXCEDED);
    }

    IPentahoSession session = PentahoSessionHolder.getSession();

    if (session == null) {
      logger.info(BAD_CREDENTIALS);
      return messages.getProperty(BAD_CREDENTIALS);
    }

    ISolutionRepository repository = PentahoSystem.get(ISolutionRepository.class, session);

    String solution = "";

    String parentFolderPath =
      ActionInfo.buildSolutionPath(solution, path, "" + ISolutionRepository.SEPARATOR);
    ISolutionFile parentSolutionFile =
      repository.getSolutionFile(parentFolderPath, ISolutionRepository.ACTION_CREATE);

    // if parentSolutionFile is null, current session user hasn't ACTION_CREATE rights
    if (parentSolutionFile == null) {
      logger.info(WRONG_FOLDER_PERMISSIONS);
      return messages.getProperty(WRONG_FOLDER_PERMISSIONS);
    }

    // check that target folder exists and is a directory
    if (!parentSolutionFile.isDirectory()) {
      logger.info(FOLDER_NOT_FOUND);
      return messages.getProperty(FOLDER_NOT_FOUND);
    }

    if (parentSolutionFile.isRoot()) {
      logger.info(WRONG_FOLDER_PERMISSIONS);
      return messages.getProperty(WRONG_FOLDER_PERMISSIONS);
    }

    String indexPath = ActionInfo.buildSolutionPath(solution, path, "");
    String repositoryBaseURL = PentahoSystem.getApplicationContext().getSolutionPath(""); //$NON-NLS-1$

    // Check if file exists to throw an error
    ISolutionFile isFile =
      repository.getSolutionFile(ActionInfo.buildSolutionPath(solution, path, fileName),
          ISolutionRepository.ACTION_EXECUTE);
    if (!overwrite && isFile != null && isFile.exists()) {
      logger.info(ALREADY_EXISTS_FILE);
      return messages.getProperty(ALREADY_EXISTS_FILE);
    }

    // publish file to the repository
    try {
      repository.publish(repositoryBaseURL, indexPath, fileName, fc, overwrite);
      repository.reloadSolutionRepository(session, ISolutionRepository.DEBUG);
    } catch (PentahoAccessControlException e) {
      logger.error(WRONG_FOLDER_PERMISSIONS, e);
      return messages.getProperty(WRONG_FOLDER_PERMISSIONS);
    }

    // create .rfp file for description
    int fileNameEndIndex = (fileName.lastIndexOf(".") == -1) ? fileName.length() - 1 : fileName.lastIndexOf(".");
    String fileBaseName = fileName.substring(0, fileNameEndIndex);
    // TODO : Use XMLBuilder?
    String fileDescriptor = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
      + "<rfp><title>" + title + "</title>"
      + "<author>RFP</author>"
      + "<description>" + desc + "</description>"
      + "<icon></icon>"
      + "</rfp>";
    try {
      repository.publish(repositoryBaseURL, indexPath, fileBaseName + ".rfp", fileDescriptor.getBytes(), true);
      repository.reloadSolutionRepository(session, ISolutionRepository.DEBUG);
    } catch (PentahoAccessControlException e) {
      logger.error(WRONG_FOLDER_PERMISSIONS, e);
      return messages.getProperty(WRONG_FOLDER_PERMISSIONS);
    }
    logger.info(FILE_CREATED);
    return messages.getProperty(FILE_CREATED);
  }
}
