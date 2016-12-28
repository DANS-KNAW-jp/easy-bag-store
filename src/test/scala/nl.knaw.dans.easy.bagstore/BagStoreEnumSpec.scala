/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.bagstore

import java.nio.file.Paths

import org.apache.commons.io.FileUtils

class BagStoreEnumSpec extends BagStoreFixture with BagStoreEnum with BagStoreAdd with BagStorePrune with BagStoreDelete {
  FileUtils.copyDirectory(Paths.get("src/test/resources/bags/basic-sequence-unpruned").toFile, testDir.toFile)
  private val TEST_BAG_A = testDir.resolve("a")
  private val TEST_BAG_B = testDir.resolve("b")
  private val TEST_BAG_C = testDir.resolve("c")

  "enumBags" should "return all BagIds" in {
    val ais = add(TEST_BAG_A).get
    val bis = add(TEST_BAG_B).get
    val cis = add(TEST_BAG_C).get

    val bagIds = enumBags().iterator.toList
    bagIds.size shouldBe 3
    bagIds.toSet shouldBe Set(ais, bis, cis)
  }

  it should "return empty stream if BagStore is empty" in {
    val bagIds = enumBags() .iterator.toList
    bagIds.size shouldBe 0
  }

  it should "skip hidden Bags by default" in {
    val ais = add(TEST_BAG_A).get
    val bis = add(TEST_BAG_B).get
    val cis = add(TEST_BAG_C).get
    delete(bis)
    val bagIds = enumBags().iterator.toList
    bagIds.size shouldBe 2
    bagIds.toSet shouldBe Set(ais, cis)
  }

  it should "include hidden Bags if requested" in {
    val ais = add(TEST_BAG_A).get
    val bis = add(TEST_BAG_B).get
    val cis = add(TEST_BAG_C).get
    delete(bis)
    val bagIds = enumBags(includeHidden = true).iterator.toList
    bagIds.size shouldBe 3
    bagIds.toSet shouldBe Set(ais, bis, cis)
  }

  it should "skip visible Bags if requested" in {
    val ais = add(TEST_BAG_A).get
    val bis = add(TEST_BAG_B).get
    val cis = add(TEST_BAG_C).get
    delete(bis)
    val bagIds = enumBags(includeVisible = false, includeHidden = true).iterator.toList
    bagIds.size shouldBe 1
    bagIds.toSet shouldBe Set(bis)

  }

  it should "skip all Bags if requested" in {
    val ais = add(TEST_BAG_A).get
    val bis = add(TEST_BAG_B).get
    val cis = add(TEST_BAG_C).get
    delete(bis)
    val bagIds = enumBags(includeVisible = false, includeHidden = false).iterator.toList
    bagIds.size shouldBe 0
    bagIds.toSet shouldBe Set()
  }

  "enumFiles" should "return all FileIds in a valid Bag" in {
    val ais = add(TEST_BAG_A).get
    val files = enumFiles(ais).iterator.toList

    files.size shouldBe 10
    files.map(_.path.getFileName.toString).toSet shouldBe Set("u", "v", "w", "x", "y", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt")
  }

  it should "return all FileIds in a virtually-valid Bag" in {
    val ais = add(TEST_BAG_A).get
    prune(TEST_BAG_B, ais)
    val bis = add(TEST_BAG_B).get
    prune(TEST_BAG_C, ais, bis)
    val cis = add(TEST_BAG_C).get
    val files = enumFiles(cis).iterator.toList

    files.size shouldBe 13
    files.map(_.path.getFileName.toString).toSet shouldBe Set("q", "w", "u", "p", "x", "y", "y-old", "z", "bag-info.txt", "bagit.txt", "manifest-md5.txt", "tagmanifest-md5.txt", "fetch.txt")

  }
}