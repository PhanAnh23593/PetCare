package com.hit.media;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

// Only the bytes are consumed by the original Cloudinary service.
record UploadBytes(byte[]bytes)implements MultipartFile{public String getName(){return"file";}public String getOriginalFilename(){return"file";}public String getContentType(){return"application/octet-stream";}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[]getBytes(){return bytes;}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File destination)throws IOException{Files.write(destination.toPath(),bytes);}}
