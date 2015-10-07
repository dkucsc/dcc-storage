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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import org.icgc.dcc.storage.client.cli.DirectoryValidator;
import org.icgc.dcc.storage.client.cli.FileValidator;
import org.icgc.dcc.storage.client.download.DownloadService;
import org.icgc.dcc.storage.client.manifest.ManifestReader;
import org.icgc.dcc.storage.client.metadata.MetadataClient;
import org.icgc.dcc.storage.client.mount.MountService;
import org.icgc.dcc.storage.client.mount.MountStorageContext;
import org.icgc.dcc.storage.client.transport.StorageService;
import org.icgc.dcc.storage.core.model.ObjectInfo;
import org.icgc.dcc.storage.fs.StorageFileSystems;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import lombok.SneakyThrows;
import lombok.val;

@Component
@Parameters(separators = "=", commandDescription = "Mounts a read-only file system view of the remote repository")
public class MountCommand extends AbstractClientCommand {

  /**
   * Options
   */
  @Parameter(names = "--mount-point", description = "the mount point of the file system", required = true, validateValueWith = DirectoryValidator.class)
  private File mountPoint;
  @Parameter(names = "--manifest", description = "path to manifest file", required = false, validateValueWith = FileValidator.class)
  private File manifestFile;

  /**
   * Dependencies.
   */
  @Autowired
  private MetadataClient metadataClient;
  @Autowired
  private DownloadService downloadService;
  @Autowired
  private StorageService storageService;
  @Autowired
  private MountService mountService;

  @Override
  @SneakyThrows
  public int execute() {
    try {
      terminal.printStatus("Indexing remote entities. Please wait...");
      val entities = metadataClient.findEntities();
      terminal.printStatus("Indexing remote objects. Please wait...");
      List<ObjectInfo> objects = storageService.listObjects();

      if (manifestFile != null) {
        val manifest = new ManifestReader().readManifest(manifestFile);

        val objectIds = manifest.getEntries().stream()
            .flatMap(entry -> Stream.of(entry.getFileUuid(), entry.getIndexFileUuid()))
            .collect(toSet());

        objects = objects.stream()
            .filter(object -> objectIds.contains(object.getId()))
            .collect(toList());
      }

      terminal.printStatus("Mounting file system to '" + mountPoint.getAbsolutePath() + "'...");
      val context = new MountStorageContext(downloadService, entities, objects);

      val fileSystem = StorageFileSystems.newFileSystem(context);
      mountService.mount(fileSystem, mountPoint.toPath());

      terminal.printStatus(
          terminal.label(
              "Successfully mounted file system at '" + terminal.value(mountPoint.getAbsolutePath())
                  + "' and is now ready for use!"));

      // Wait for interrupt
      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      terminal.printStatus("Unmounting file system...");
    }

    return SUCCESS_STATUS;
  }

}
