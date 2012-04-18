/**
 * 
 */
package cn.bc.docs.web.struts2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

import cn.bc.core.util.DateUtils;
import cn.bc.docs.domain.Attach;
import cn.bc.docs.web.AttachUtils;
import cn.bc.web.util.WebUtils;

import com.artofsolving.jodconverter.DefaultDocumentFormatRegistry;
import com.artofsolving.jodconverter.DocumentConverter;
import com.artofsolving.jodconverter.DocumentFormatRegistry;
import com.artofsolving.jodconverter.openoffice.connection.OpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.connection.SocketOpenOfficeConnection;
import com.artofsolving.jodconverter.openoffice.converter.OpenOfficeDocumentConverter;
import com.opensymphony.xwork2.ActionSupport;

/**
 * 直接的物理附件处理Action
 * 
 * @author dragon
 * 
 */
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Controller
public class FileAction extends ActionSupport {
	private static Log logger = LogFactory.getLog(FileAction.class);
	private static final long serialVersionUID = 1L;

	public String filename;
	public String contentType;
	public long contentLength;
	public InputStream inputStream;
	public String path;

	// 下载附件
	public String download() throws Exception {
		Date startTime = new Date();
		// 附件的绝对路径名
		String path = Attach.DATA_REAL_PATH + File.separator + this.f;

		// 附件的扩展名
		String extension = StringUtils.getFilenameExtension(path);

		// 下载的文件名
		if (this.n == null || this.n.length() == 0)
			this.n = StringUtils.getFilename(this.f);
		if (this.n.lastIndexOf(".") == -1)
			this.n += "." + extension;

		// debug
		if (logger.isDebugEnabled()) {
			logger.debug("path=" + path);
			logger.debug("extension=" + extension);
			logger.debug("n=" + n);
		}

		// 设置下载文件的参数
		this.contentType = AttachUtils.getContentType(extension);
		this.filename = WebUtils.encodeFileName(
				ServletActionContext.getRequest(), this.n);
		File file = new File(path);
		this.contentLength = file.length();
		this.inputStream = new FileInputStream(file);
		if (logger.isDebugEnabled()) {
			logger.debug("download:" + DateUtils.getWasteTime(startTime));
		}

		return SUCCESS;
	}

	private static final int BUFFER = 4096;
	public String to;// 预览时转换到的文件类型，默认为pdf
	public String f;// 要下载的文件，相对于Attach.DATA_REAL_PATH下的子路径，前后均不带/
	public String n;// [可选]指定下载为的文件名

	// 支持在线打开文档查看的文件下载
	public String inline() throws Exception {
		Date startTime = new Date();
		// 附件的绝对路径名
		String path = Attach.DATA_REAL_PATH + File.separator + this.f;

		// 附件的扩展名
		String extension = StringUtils.getFilenameExtension(path);

		// 下载的文件名
		if (this.n == null || this.n.length() == 0)
			this.n = StringUtils.getFilename(this.f);

		// debug
		if (logger.isDebugEnabled()) {
			logger.debug("path=" + path);
			logger.debug("extension=" + extension);
			logger.debug("n=" + n);
			logger.debug("to=" + to);
		}

		if (isConvertFile(extension)) {
			// 调用jodconvert将附件转换为pdf文档后再下载
			FileInputStream inputStream = new FileInputStream(new File(path));
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream(
					BUFFER);

			// connect to an OpenOffice.org instance running on port 8100
			OpenOfficeConnection connection = new SocketOpenOfficeConnection(
					getText("jodconverter.soffice.host"),
					Integer.parseInt(getText("jodconverter.soffice.port")));
			connection.connect();
			if (logger.isDebugEnabled()) {
				logger.debug("connect:" + DateUtils.getWasteTime(startTime));
			}

			DocumentFormatRegistry formaters = new DefaultDocumentFormatRegistry();

			// convert
			DocumentConverter converter = new OpenOfficeDocumentConverter(
					connection);
			String from = extension;
			if (this.to == null || this.to.length() == 0)
				this.to = getText("jodconverter.to.extension");// 没有指定就是用系统默认的配置转换为pdf
			converter.convert(inputStream,
					formaters.getFormatByFileExtension(from), outputStream,
					formaters.getFormatByFileExtension(this.to));
			if (logger.isDebugEnabled()) {
				logger.debug("convert:" + DateUtils.getWasteTime(startTime));
			}

			// close the connection
			connection.disconnect();

			// 设置下载文件的参数（设置不对的话，浏览器是不会直接打开的）
			byte[] bs = outputStream.toByteArray();
			this.inputStream = new ByteArrayInputStream(bs);
			this.inputStream.close();
			this.contentType = AttachUtils.getContentType(this.to);
			this.contentLength = bs.length;
			this.filename = WebUtils.encodeFileName(ServletActionContext
					.getRequest(), this.n == null ? "bc" : this.n + "."
					+ this.to);
		} else {
			// 设置下载文件的参数
			this.contentType = AttachUtils.getContentType(extension);
			this.filename = WebUtils.encodeFileName(
					ServletActionContext.getRequest(), this.n);

			// 无需转换的文档直接下载处理
			File file = new File(path);
			this.contentLength = file.length();
			this.inputStream = new FileInputStream(file);
		}

		return SUCCESS;
	}

	// 判断指定的扩展名是否为配置的要转换的文件类型
	private boolean isConvertFile(String extension) {
		String[] extensions = getText("jodconverter.from.extensions")
				.split(",");
		for (String ext : extensions) {
			if (ext.equalsIgnoreCase(extension))
				return true;
		}
		return false;
	}
}
