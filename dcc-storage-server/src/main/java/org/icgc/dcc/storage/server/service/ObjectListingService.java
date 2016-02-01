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
package org.icgc.dcc.storage.server.service;

import static org.icgc.dcc.storage.core.util.UUIDs.isUUID;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.storage.core.model.ObjectInfo;
import org.icgc.dcc.storage.server.util.BucketNamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;

@Slf4j
@Service
public class ObjectListingService {

  /**
   * Configuration.
   */
  @Value("${bucket.name.object}")
  private String bucketName;
  @Value("${collaboratory.data.directory}")
  private String dataDir;
  // @Value("${collaboratory.bucket.poolsize}")
  // private int bucketPoolSize;
  // @Value("${collaboratory.bucket.keysize}")
  // private int bucketKeySize;

  /**
   * Dependencies.
   */
  @Autowired
  private AmazonS3 s3;
  @Autowired
  private BucketNamingService bucketNamingService;

  @Cacheable("listing")
  public List<ObjectInfo> getListing() {
    val listing = Lists.<ObjectInfo> newArrayList();

    for (int i = 0; i < bucketNamingService.getBucketPoolSize(); i++) {
      readBucket(bucketNamingService.constructBucketName(bucketName, i), dataDir, (objectSummary) -> {
        ObjectInfo info = createInfo(objectSummary);
        if (info.getId() != null) {
          listing.add(info);
        }
      });
    }

    return listing;
  }

  private void readBucket(String bucketName, String prefix, Consumer<S3ObjectSummary> callback) {
    val request = new ListObjectsRequest().withBucketName(bucketName).withPrefix(prefix);
    log.debug("Reading summaries from '{}/{}'...", bucketName, prefix);

    ObjectListing listing;
    do {
      listing = s3.listObjects(request);
      for (val objectSummary : listing.getObjectSummaries()) {
        callback.accept(objectSummary);
      }
      request.setMarker(listing.getNextMarker());
    } while (listing.isTruncated());
  }

  private ObjectInfo createInfo(S3ObjectSummary objectSummary) {
    return new ObjectInfo(
        getObjectId(objectSummary),
        objectSummary.getLastModified().getTime(),
        objectSummary.getSize());
  }

  private static String getObjectId(S3ObjectSummary objectSummary) {
    val name = new File(objectSummary.getKey()).getName();

    // Only UUIDs correspond to published objects
    return isUUID(name) ? name : null;
  }

}
