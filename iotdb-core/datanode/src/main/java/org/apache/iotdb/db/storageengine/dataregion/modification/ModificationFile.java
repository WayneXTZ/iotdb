/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.storageengine.dataregion.modification;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModificationFile implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModificationFile.class);

  private final File file;
  private FileChannel channel;
  private OutputStream fileOutputStream;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private final Set<TsFileResource> tsFileRefs = new ConcurrentSkipListSet<>(Comparator.comparing(
      TsFileResource::getTsFilePath));

  public ModificationFile(String filePath, TsFileResource firstResource) throws IOException {
    this.file = new File(filePath);
    tsFileRefs.add(firstResource);
  }

  public void write(ModEntry entry) throws IOException {
    if (fileOutputStream == null) {
      fileOutputStream = new BufferedOutputStream(Files.newOutputStream(file.toPath()));
      channel = FileChannel.open(file.toPath());
    }
    entry.serialize(fileOutputStream);
    channel.force(false);
  }

  public Iterator<ModEntry> getModIterator() throws IOException {
    return new ModIterator();
  }

  @Override
  public void close() throws Exception {
    fileOutputStream.close();
    fileOutputStream = null;
    channel.close();
    channel = null;
  }

  /**
   * Add a TsFile to the reference set only if the set is not empty.
   * @param tsFile TsFile to be added
   * @return true if the TsFile is successfully added, false if the reference set is empty.
   */
  public boolean addReference(TsFileResource tsFile) {
    // adding references can be concurrent, but adding and removing cannot
    lock.readLock().lock();
    try {
      if (!tsFileRefs.isEmpty()) {
        tsFileRefs.add(tsFile);
        return true;
      }
      return false;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Remove the references of the given TsFiles.
   * @param tsFiles references to remove
   * @return true if the ref set is empty after removal, false otherwise
   */
  public boolean removeReferences(List<TsFileResource> tsFiles) {
    lock.writeLock().lock();
    try {
      tsFiles.forEach(tsFileRefs::remove);
      return tsFileRefs.isEmpty();
    } finally {
      lock.writeLock().unlock();
    }
  }

  public class ModIterator implements Iterator<ModEntry>, AutoCloseable {
    private InputStream inputStream;
    private ModEntry nextEntry;

    public ModIterator() throws IOException {
      this.inputStream = Files.newInputStream(file.toPath());
    }

    @Override
    public void close() {
      try {
        inputStream.close();
      } catch (IOException e) {
        LOGGER.info("Cannot close mod file input stream of {}", file, e);
      } finally {
        inputStream = null;
      }
    }

    @Override
    public boolean hasNext() {
      if (inputStream == null) {
        return false;
      }
      if (nextEntry == null) {
        try {
          if (inputStream.available() == 0) {
            return false;
          }
          nextEntry = ModEntry.createFrom(inputStream);
        } catch (EOFException e) {
          close();
        } catch (IOException e) {
          LOGGER.info("Cannot read mod file input stream of {}", file, e);
          close();
        }
      }

      return nextEntry != null;
    }

    @Override
    public ModEntry next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      ModEntry ret = nextEntry;
      nextEntry = null;
      return ret;
    }
  }
}
