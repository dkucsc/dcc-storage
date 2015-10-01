/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.storage.client.command;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.icgc.dcc.storage.client.cli.ObjectIdValidator;
import org.icgc.dcc.storage.client.cli.OutputTypeConverter;
import org.icgc.dcc.storage.client.download.DownloadService;
import org.icgc.dcc.storage.client.metadata.Entity;
import org.icgc.dcc.storage.client.metadata.MetadataService;
import org.icgc.dcc.storage.client.slicing.QueryHandler;
import org.icgc.dcc.storage.client.transport.NullSourceSeekableHTTPStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import lombok.Cleanup;
import lombok.val;

@Component
@Parameters(separators = "=", commandDescription = "Extract/displays some or all of SAM/BAM file")
public class ViewCommand extends AbstractClientCommand {

  /**
   * Options.
   */
  @Parameter(names = "--contained", description = "output only alignments completely contained in specified region. By default, any alignment"
      + " that intersects with a specified region will be returned")
  private boolean containedOnly = false;

  @Parameter(names = "--header-only", description = "output header of SAM/BAM file only")
  private boolean headerOnly = false;

  @Parameter(names = "--no-header", description = "do not output a header of SAM/BAM file when viewing")
  private boolean noHeader = false;

  @Parameter(names = "--output-path", description = "path to an output directory. Stdout if not specified.")
  private String filePath = "";

  @Parameter(names = "--output-file", description = "filename to write output to. Uses filename from metadata, or original input filename if not specified")
  private String fileName = "";

  @Parameter(names = "--output-type", description = "output format of query BAM/SAM.", converter = OutputTypeConverter.class)
  private OutputType outputType = OutputType.bam;

  @Parameter(names = "--object-id", description = "object id of BAM file to download slice from", validateValueWith = ObjectIdValidator.class)
  private String oid;

  @Parameter(names = "--input-file", description = "local path to BAM file. Overrides specification of --object-id")
  private String bamFilePath = "";

  @Parameter(names = "--input-file-index", description = "explicit local path to index file (requires --input-file)")
  private String baiFilePath = "";

  @Parameter(names = "--query", description = "query to define extract from BAM file (coordinate format 'sequence:start-end'). Multiple"
      + " ranges separated by space", variableArity = true)
  private List<String> query = new ArrayList<String>();

  public enum OutputType {
    bam, sam
  }

  /**
   * Dependencies.
   */
  @Autowired
  private MetadataService metadataService;
  @Autowired
  private DownloadService downloadService;

  @Override
  public int execute() {
    try {
      println("\rViewing...   ");
      val entity = getEntity();
      val resource = createInputResource(entity);

      @Cleanup
      val reader = createSamReader(resource);
      val header = noHeader ? emptyHeader() : reader.getFileHeader();

      val outputFileName = generateFileOutputName(entity);
      @Cleanup
      val writer = createSamFileWriter(header, outputFileName);

      if (!headerOnly) {
        // Perform actual slicing
        QueryInterval[] intervals = QueryHandler.parseQueryStrings(header, query);
        val iterator = reader.query(intervals, containedOnly);

        while (iterator.hasNext()) {
          val record = iterator.next();
          writer.addAlignment(record);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    return SUCCESS_STATUS;
  }

  private Optional<Entity> getEntity() {
    return Optional.ofNullable(oid != null && !oid.trim().isEmpty() ? metadataService.getEntity(oid) : null);
  }

  private SamReader createSamReader(SamInputResource resource) {
    // Need to use non-STRICT due to header date formats in the wild.
    return SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT).open(resource);
  }

  private SamInputResource createInputResource(Optional<Entity> entity) {
    SamInputResource resource = null;
    if (entity.isPresent()) {
      resource = getRemoteResource(entity.get());
    } else {
      if (bamFilePath.trim().isEmpty()) {
        throw new IllegalArgumentException("No BAM file input specified");
      } else if (baiFilePath.trim().isEmpty()) {
        resource = getFileResource(bamFilePath);
      } else {
        resource = getFileResource(bamFilePath, baiFilePath);
      }
    }
    return resource;
  }

  private SAMFileWriter createSamFileWriter(SAMFileHeader header, String path) {
    val stdout = (path == null) || path.trim().isEmpty();
    val factory = new SAMFileWriterFactory().setCreateIndex(true).setUseAsyncIo(true).setCreateMd5File(false);

    SAMFileWriter result = null;
    if (outputType == OutputType.bam) {
      result = stdout ? factory.makeBAMWriter(header, true, System.out) : factory.makeBAMWriter(header, true,
          new File(path));
    } else if (outputType == OutputType.sam) {
      result = stdout ? factory.makeSAMWriter(header, true, System.out) : factory.makeSAMWriter(header, true,
          new File(path));
    }
    return result;
  }

  private String generateFileOutputName(Optional<Entity> entity) {
    String result = ""; // stdout
    // check for explicit path + filename
    if (!filePath.trim().isEmpty()) {
      if (fileName.trim().isEmpty()) {
        // generated name depends on whether user has specified object id or local bam file name
        result = generateDefaultFilename(entity);
      } else {
        // use supplied filename
        result = filePath + File.separator + fileName;
      }
    }
    return result;
  }

  private String generateDefaultFilename(Optional<Entity> entity) {
    String result = "";
    if (bamFilePath.trim().isEmpty()) {
      result = filePath + File.separator + entity.get().getFileName();
    } else {
      // use filename of input file
      String fileName = new File(bamFilePath).getName();
      result = filePath + File.separator + "extract-" + fileName;
    }
    return result;
  }

  /**
   * Assumes that the .BAI index file has the same name as the bamFilePath parameter: <sam/bam file name>.bam.bai
   * 
   */
  private SamInputResource getFileResource(String bamFilePath) {
    return getFileResource(bamFilePath, bamFilePath + ".bai");
  }

  private SamInputResource getFileResource(String bamFilePath, String baiFilePath) {
    val bam = new File(bamFilePath);
    checkArgument(bam.exists(), "Input BAM file '%s' not found", bamFilePath);

    if (outputType == OutputType.bam) {
      val bai = new File(baiFilePath);
      checkArgument(bai.exists(),
          "Input BAI file '%s' not found. Consider setting filename with --input-file-index option", baiFilePath);

      return SamInputResource.of(bam).index(bai);
    } else {
      return SamInputResource.of(bam);
    }
  }

  private SamInputResource getRemoteResource(Entity entity) {
    val bamFileUrl = downloadService.getUrl(entity.getId(), 0, -1);

    val indexEntity = metadataService.getIndexEntity(entity);
    checkState(indexEntity.isPresent(), "No index file associated with BAM file (object_id = %s)", entity);

    val indexFileUrl = downloadService.getUrl(indexEntity.get().getId());

    val bamFileHttpStream = new NullSourceSeekableHTTPStream(bamFileUrl);
    val indexFileHttpStream = new NullSourceSeekableHTTPStream(indexFileUrl);

    val resource = SamInputResource.of(bamFileHttpStream).index(indexFileHttpStream);
    return resource;
  }

  private static SAMFileHeader emptyHeader() {
    val header = new SAMFileHeader();
    header.setAttribute(SAMFileHeader.VERSION_TAG, null); // Unset this since it was set in the ctor

    return header;
  }

}
