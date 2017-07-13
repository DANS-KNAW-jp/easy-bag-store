package nl.knaw.dans.easy.bagstore.server

import java.io.{ FileOutputStream, InputStream }
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }
import java.util.UUID

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import nl.knaw.dans.easy.bagstore.{ BagStoresFixture, Bagit4Fixture, TestSupportFixture, pathsEqual }
import nl.knaw.dans.easy.bagstore.component.{ BagProcessingComponent, BagStoreComponent, BagStoresComponent, FileSystemComponent }
import org.apache.commons.io.FileUtils
import org.scalatra.test.scalatest.ScalatraSuite

import scala.io.Source

class StoresServletSpec extends TestSupportFixture
  with Bagit4Fixture
  with BagStoresFixture
  with ScalatraSuite
  with StoresServletComponent
  with BagStoresComponent
  with BagStoreComponent
  with BagProcessingComponent
  with FileSystemComponent {

  val storesServlet: StoresServlet = new StoresServlet {
    val externalBaseUri: URI = new URI("http://example-archive.org/")
  }

  private val TEST_BAGS_UNPRUNED = testDir.resolve("basic-sequence-unpruned-with-refbags")
  FileUtils.copyDirectory(
    Paths.get("src/test/resources/bags/basic-sequence-unpruned-with-refbags").toFile,
    TEST_BAGS_UNPRUNED.toFile)
  private val bagInput = Files.createDirectory(testDir.resolve("bag-input"))
  private val TEST_BAG_UNPRUNED_A = bagInput.resolve("unpruned-with-refbags-a.zip")
  private val TEST_BAG_UNPRUNED_B = bagInput.resolve("unpruned-with-refbags-b.zip")
  private val TEST_BAG_UNPRUNED_C = bagInput.resolve("unpruned-with-refbags-c.zip")

  new ZipFile(TEST_BAG_UNPRUNED_A.toFile) {
    addFolder(TEST_BAGS_UNPRUNED.resolve("a").toFile, new ZipParameters)
  }
  new ZipFile(TEST_BAG_UNPRUNED_B.toFile) {
    addFolder(TEST_BAGS_UNPRUNED.resolve("b").toFile, new ZipParameters)
  }
  new ZipFile(TEST_BAG_UNPRUNED_C.toFile) {
    addFolder(TEST_BAGS_UNPRUNED.resolve("c").toFile, new ZipParameters)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    addServlet(storesServlet, "/*")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/00").toFile, store1.toFile)
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/00").toFile, store2.toFile)
    Files.move(store2.resolve("00/000000000000000000000000000001"), store2.resolve("00/000000000000000000000000000004"))
    Files.move(store2.resolve("00/000000000000000000000000000002"), store2.resolve("00/000000000000000000000000000005"))
    Files.move(store2.resolve("00/000000000000000000000000000003"), store2.resolve("00/000000000000000000000000000006"))
  }

  private def setBag1Hidden(): Unit = {
    Files.move(store1.resolve("00/000000000000000000000000000001/bag-revision-1"), store1.resolve("00/000000000000000000000000000001/.bag-revision-1"))
  }

  private def makeBagstore1Invalid(): Unit = {
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/ff").toFile, store1.toFile)
  }

  private def makeBagstore2Invalid(): Unit = {
    FileUtils.copyDirectoryToDirectory(Paths.get("src/test/resources/bag-store/ff").toFile, store2.toFile)
  }

  "get /:bagstore/bags" should "enumerate all bags in a specific store" in {
    get("/store1/bags") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate inactive bags only when this is set" in {
    setBag1Hidden()
    get("/store1/bags", "state" -> "inactive") {
      status shouldBe 200
      body.lines.toList should contain only "00000000-0000-0000-0000-000000000001"
    }
  }

  it should "enumerate active bags only by default" in {
    setBag1Hidden()
    get("/store1/bags") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate all bags when this is set" in {
    setBag1Hidden()
    get("/store1/bags", "state" -> "all") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "enumerate the bags in all bag-stores even if an unknown state is given" in {
    get("/store1/bags", "state" -> "invalid value") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "return an empty string when all bag-stores are empty" in {
    FileUtils.deleteDirectory(store1.resolve("00").toFile)
    get("/store1/bags") {
      status shouldBe 200
      body shouldBe empty
    }
  }

  it should "return a 500 error when the store is corrupt" in {
    makeBagstore1Invalid()

    get("/store1/bags") {
      status shouldBe 500
      body should include("Unexpected type of failure. Please consult the logs")
    }
  }

  it should "function normally when another store is corrupt" in {
    makeBagstore2Invalid()

    get("/store1/bags") {
      status shouldBe 200
      body.lines.toList should contain allOf(
        "00000000-0000-0000-0000-000000000001",
        "00000000-0000-0000-0000-000000000002",
        "00000000-0000-0000-0000-000000000003"
      )
    }
  }

  it should "fail when an unknown store is given" in {
    get("/unknown-store/bags") {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  "get /:bagstore/bags/:uuid" should "return an overview of the files in a given bag in a certain store" in {
    get("/store1/bags/00000000-0000-0000-0000-000000000001") {
      status shouldBe 200
      body.lines.toList should contain only(
        "00000000-0000-0000-0000-000000000001/bagit.txt",
        "00000000-0000-0000-0000-000000000001/data/y",
        "00000000-0000-0000-0000-000000000001/manifest-sha1.txt",
        "00000000-0000-0000-0000-000000000001/data/x",
        "00000000-0000-0000-0000-000000000001/data/sub/w",
        "00000000-0000-0000-0000-000000000001/data/z",
        "00000000-0000-0000-0000-000000000001/data/sub/u",
        "00000000-0000-0000-0000-000000000001/tagmanifest-sha1.txt",
        "00000000-0000-0000-0000-000000000001/data/sub/v",
        "00000000-0000-0000-0000-000000000001/bag-info.txt"
      )
    }
  }

  it should "fail when the store is unknown" in {
    get("/unknown-store/bags/00000000-0000-0000-0000-000000000001") {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    get("/store1/bags/00000000000000000000000000000001") {
      status shouldBe 400
      body shouldBe "invalid UUID string: 00000000000000000000000000000001"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    get("/store1/bags/abc-def-ghi-jkl-mno") {
      status shouldBe 400
      body shouldBe "invalid UUID string: abc-def-ghi-jkl-mno"
    }
  }

  it should "fail when the bag is not found" in {
    val uuid = UUID.randomUUID()
    get(s"/store1/bags/$uuid") {
      status shouldBe 404
      body shouldBe s"Bag $uuid does not exist in BagStore"
    }
  }

  it should "return an overview when text/plain is specified for content negotiation" in {
    get("/store1/bags/00000000-0000-0000-0000-000000000001", params = Map.empty, headers = Map("Accept" -> "text/plain")) {
      status shouldBe 200
      body.lines.toList should contain only(
        "00000000-0000-0000-0000-000000000001/bagit.txt",
        "00000000-0000-0000-0000-000000000001/data/y",
        "00000000-0000-0000-0000-000000000001/manifest-sha1.txt",
        "00000000-0000-0000-0000-000000000001/data/x",
        "00000000-0000-0000-0000-000000000001/data/sub/w",
        "00000000-0000-0000-0000-000000000001/data/z",
        "00000000-0000-0000-0000-000000000001/data/sub/u",
        "00000000-0000-0000-0000-000000000001/tagmanifest-sha1.txt",
        "00000000-0000-0000-0000-000000000001/data/sub/v",
        "00000000-0000-0000-0000-000000000001/bag-info.txt"
      )
    }
  }

  it should "return the bag itself when application/zip is specified for content negotiation" in {
    get("/store1/bags/00000000-0000-0000-0000-000000000001", params = Map.empty, headers = Map("Accept" -> "application/zip")) {
      status shouldBe 200

      val zip = testDir.resolve("bag-output/00000000-0000-0000-0000-000000000001.zip")
      val unzip = testDir.resolve("bag-output/00000000-0000-0000-0000-000000000001")
      Files.createDirectories(zip.getParent)
      Files.copy(response.inputStream, zip)
      zip.toFile should exist

      new ZipFile(zip.toFile) {
        setFileNameCharset(StandardCharsets.UTF_8.name)
      }.extractAll(unzip.toAbsolutePath.toString)
      unzip.toFile should exist

      pathsEqual(unzip, store1.resolve("00/000000000000000000000000000001")) shouldBe true
    }
  }

  "get /:bagstore/bags/:uuid/*" should "return a specific file in the bag indicated by the path" in {
    get("/store1/bags/00000000-0000-0000-0000-000000000001/data/y") {
      status shouldBe 200

      Source.fromInputStream(response.inputStream).mkString shouldBe
        Source.fromFile(store1.resolve("00/000000000000000000000000000001/bag-revision-1/data/y").toFile).mkString
    }
  }

  it should "return a metadata file in the bag indicated by the path" in {
    get("/store1/bags/00000000-0000-0000-0000-000000000001/metadata/files.xml") {
      status shouldBe 200

      Source.fromInputStream(response.inputStream).mkString shouldBe
        Source.fromFile(store1.resolve("00/000000000000000000000000000001/bag-revision-1/metadata/files.xml").toFile).mkString
    }
  }

  it should "fail when the store is unknown" in {
    get("/unknown-store/bags/00000000-0000-0000-0000-000000000001/data/y") {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    get("/store1/bags/00000000000000000000000000000001/data/y") {
      status shouldBe 400
      body shouldBe "invalid UUID string: 00000000000000000000000000000001"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    get("/store1/bags/abc-def-ghi-jkl-mno/data/y") {
      status shouldBe 400
      body shouldBe "invalid UUID string: abc-def-ghi-jkl-mno"
    }
  }

  it should "fail when the bag is not found" in {
    val uuid = UUID.randomUUID()
    get(s"/store1/bags/$uuid/data/y") {
      status shouldBe 404
      body shouldBe s"Bag $uuid does not exist in BagStore"
    }
  }

  it should "fail when the file is not found" in {
    get("/store1/bags/00000000-0000-0000-0000-000000000001/unknown-folder/unknown-file") {
      status shouldBe 404
      body shouldBe s"File 00000000-0000-0000-0000-000000000001/unknown-folder/unknown-file does not exist in bag 00000000-0000-0000-0000-000000000001"
    }
  }

  "put /:bagstore/bags/:uuid" should "store a bag in the given bag-store" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(TEST_BAG_UNPRUNED_A)) {
      status shouldBe 201
      header should contain("Location" -> s"http://example-archive.org/stores/store1/bags/$uuid")
    }
  }

  it should "store and prune multiple revisions of a bagsequence" in {
    val uuid1 = "11111111-1111-1111-1111-111111111111"
    put(s"/store1/bags/$uuid1", body = Files.readAllBytes(TEST_BAG_UNPRUNED_A)) {
      status shouldBe 201
      header should contain("Location" -> s"http://example-archive.org/stores/store1/bags/$uuid1")
    }

    val uuid2 = "11111111-1111-1111-1111-111111111112"
    put(s"/store1/bags/$uuid2", body = Files.readAllBytes(TEST_BAG_UNPRUNED_B)) {
      status shouldBe 201
      header should contain("Location" -> s"http://example-archive.org/stores/store1/bags/$uuid2")
    }

    val uuid3 = "11111111-1111-1111-1111-111111111113"
    put(s"/store1/bags/$uuid3", body = Files.readAllBytes(TEST_BAG_UNPRUNED_C)) {
      status shouldBe 201
      header should contain("Location" -> s"http://example-archive.org/stores/store1/bags/$uuid3")
    }

    val pruned = Paths.get("src/test/resources/bags/basic-sequence-pruned")
    pathsEqual(pruned.resolve("a"), store1.resolve("11/111111111111111111111111111111/a")) shouldBe true
    pathsEqual(pruned.resolve("b"), store1.resolve("11/111111111111111111111111111112/b"), "fetch.txt") shouldBe true
    pathsEqual(pruned.resolve("c"), store1.resolve("11/111111111111111111111111111113/c"), "fetch.txt") shouldBe true
  }

  it should "fail when the store is unknown" in {
    put("/unknown-store/bags/11111111-1111-1111-1111-111111111111", body = Files.readAllBytes(TEST_BAG_UNPRUNED_A)) {
      status shouldBe 404
      body shouldBe "No such bag-store: unknown-store"
    }
  }

  it should "fail when the given uuid is not a uuid" in {
    val uuid = "abcde"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(TEST_BAG_UNPRUNED_A)) {
      status shouldBe 400
      body shouldBe s"invalid UUID string: $uuid"
    }
  }

  it should "fail when the given uuid is not a well-formatted uuid" in {
    val uuid = "abc-def-ghi-jkl-mno"
    put(s"/store1/bags/$uuid") {
      status shouldBe 400
      body shouldBe s"invalid UUID string: $uuid"
    }
  }

  it should "fail when the input stream is empty" in {
    val uuid = "11111111-1111-1111-1111-111111111111"
    put("/store1/bags/11111111-1111-1111-1111-111111111111") {
      status shouldBe 400
      body shouldBe "The provided input did not contain a bag"
    }
  }

  it should "fail when the input stream contains anything else than a zip-file" in {
    val uuid = "66666666-6666-6666-6666-666666666666"
    put(s"/store1/bags/$uuid", body = "hello world".getBytes) {
      status shouldBe 400
      body shouldBe "The provided input did not contain a bag"
    }
  }

  it should "fail when the input stream contains a zip-file that doesn't represent a bag" in {
    val zip = Files.createDirectory(testDir.resolve("failing-input")).resolve("failing.zip")
    new ZipFile(zip.toFile) {
      addFolder(TEST_BAGS_UNPRUNED.resolve("a/data").toFile, new ZipParameters)
    }

    val uuid = "66666666-6666-6666-6666-666666666666"
    put(s"/store1/bags/$uuid", body = Files.readAllBytes(zip)) {
      status shouldBe 400
      body shouldBe s"Bag $uuid is not a valid bag"
    }
  }
}
