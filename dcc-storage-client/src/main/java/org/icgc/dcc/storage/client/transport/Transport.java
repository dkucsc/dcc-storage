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
package org.icgc.dcc.storage.client.transport;

import java.io.File;
import java.util.List;

import org.icgc.dcc.storage.client.progress.Progress;
import org.icgc.dcc.storage.core.model.Part;

/**
 * A transport for data upload
 */
public interface Transport {

  enum Mode {
    UPLOAD, DOWNLOAD
  };

  /**
   * Send a given file to collaboratory
   * @param file
   */
  public void send(File file);

  public void receive(File file);

  /**
   * A builder interface for the data transport
   */
  public interface Builder {

    public Transport build();

    public Builder withProgressBar(Progress progressBar);

    public Builder withParts(List<Part> parts);

    public Builder withObjectId(String objectId);

    public Builder withSessionId(String uploadId);

    public Builder withProxy(StorageService proxy);

    public Builder withTransportMode(Mode mode);

    public Builder withChecksum(boolean checksum);
  }

  public abstract class AbstractBuilder implements Builder {

    protected StorageService proxy;
    protected Progress progressBar;
    protected List<Part> parts;
    protected String objectId;
    protected String uploadId;
    protected Mode mode;
    protected boolean checksum;

    @Override
    public Builder withProgressBar(Progress progressBar) {
      this.progressBar = progressBar;
      return this;
    }

    @Override
    public Builder withParts(List<Part> parts) {
      this.parts = parts;
      return this;
    }

    @Override
    public Builder withObjectId(String objectId) {
      this.objectId = objectId;
      return this;
    }

    @Override
    public Builder withTransportMode(Mode mode) {
      this.mode = mode;
      return this;
    }

    @Override
    public Builder withSessionId(String uploadId) {
      this.uploadId = uploadId;
      return this;
    }

    @Override
    public Builder withProxy(StorageService proxy) {
      this.proxy = proxy;
      return this;
    }

    @Override
    public Builder withChecksum(boolean checksum) {
      this.checksum = checksum;
      return this;
    }

  }

}