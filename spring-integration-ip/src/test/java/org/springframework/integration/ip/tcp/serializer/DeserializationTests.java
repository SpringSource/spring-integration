/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ip.tcp.serializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import javax.net.ServerSocketFactory;

import org.junit.Test;

import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.integration.ip.util.SocketTestUtils;
import org.springframework.integration.test.util.SocketUtils;

/**
 * @author Gary Russell
 * @author Gavin Gray
 * @since 2.0
 */
public class DeserializationTests {

	@Test
	public void testReadLength() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendLength(port, null);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayLengthHeaderSerializer serializer = new ByteArrayLengthHeaderSerializer();
		byte[] out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
								 new String(out));
		out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
				 new String(out));
		server.close();
		done.countDown();
	}

	@Test
	public void testReadStxEtx() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendStxEtx(port, null);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayStxEtxSerializer serializer = new ByteArrayStxEtxSerializer();
		byte[] out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
								 new String(out));
		out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
				 new String(out));
		server.close();
		done.countDown();
	}

	@Test
	public void testReadCrLf() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendCrLf(port, null);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		byte[] out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
								 new String(out));
		out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
				 new String(out));
		server.close();
		done.countDown();
	}

	@Test
	public void testReadRaw() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		SocketTestUtils.testSendRaw(port);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayRawSerializer serializer = new ByteArrayRawSerializer();
		byte[] out = serializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING + SocketTestUtils.TEST_STRING,
								 new String(out));
		server.close();
	}

	@Test
	public void testReadSerialized() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendSerialized(port);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		DefaultDeserializer deserializer = new DefaultDeserializer();
		Object out = deserializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING, out);
		out = deserializer.deserialize(socket.getInputStream());
		assertEquals("Data", SocketTestUtils.TEST_STRING, out);
		server.close();
		done.countDown();
	}

	@Test
	public void testReadLengthOverflow() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendLengthOverflow(port);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayLengthHeaderSerializer serializer = new ByteArrayLengthHeaderSerializer();
		try {
			serializer.deserialize(socket.getInputStream());
	    	fail("Expected message length exceeded exception");
		} catch (IOException e) {
			if (!e.getMessage().startsWith("Message length")) {
				e.printStackTrace();
				fail("Unexpected IO Error:" + e.getMessage());
			}
		}
		server.close();
		done.countDown();
	}

	@Test
	public void testReadStxEtxTimeout() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendStxEtxOverflow(port);
		Socket socket = server.accept();
		socket.setSoTimeout(500);
		ByteArrayStxEtxSerializer serializer = new ByteArrayStxEtxSerializer();
		try {
			serializer.deserialize(socket.getInputStream());
	    	fail("Expected timeout exception");
		} catch (IOException e) {
			if (!e.getMessage().startsWith("Read timed out")) {
				e.printStackTrace();
				fail("Unexpected IO Error:" + e.getMessage());
			}
		}
		server.close();
		done.countDown();
	}

	@Test
	public void testReadStxEtxOverflow() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch done = SocketTestUtils.testSendStxEtxOverflow(port);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayStxEtxSerializer serializer = new ByteArrayStxEtxSerializer();
		serializer.setMaxMessageSize(1024);
		try {
			serializer.deserialize(socket.getInputStream());
	    	fail("Expected message length exceeded exception");
		} catch (IOException e) {
			if (!e.getMessage().startsWith("ETX not found")) {
				e.printStackTrace();
				fail("Unexpected IO Error:" + e.getMessage());
			}
		}
		server.close();
		done.countDown();
	}

	@Test
	public void testReadCrLfTimeout() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch latch = SocketTestUtils.testSendCrLfOverflow(port);
		Socket socket = server.accept();
		socket.setSoTimeout(500);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		try {
			serializer.deserialize(socket.getInputStream());
	    	fail("Expected timout exception");
		} catch (IOException e) {
			if (!e.getMessage().startsWith("Read timed out")) {
				e.printStackTrace();
				fail("Unexpected IO Error:" + e.getMessage());
			}
		}
		server.close();
		latch.countDown();
	}

	@Test
	public void testReadCrLfOverflow() throws Exception {
		int port = SocketUtils.findAvailableServerSocket();
		ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
		server.setSoTimeout(10000);
		CountDownLatch latch = SocketTestUtils.testSendCrLfOverflow(port);
		Socket socket = server.accept();
		socket.setSoTimeout(5000);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		serializer.setMaxMessageSize(1024);
		try {
			serializer.deserialize(socket.getInputStream());
	    	fail("Expected message length exceeded exception");
		}
		catch (IOException e) {
			if (!e.getMessage().startsWith("CRLF not found")) {
				e.printStackTrace();
				fail("Unexpected IO Error:" + e.getMessage());
			}
		}
		server.close();
		latch.countDown();
	}

    @Test
    public void canDeserializeMultipleSubsequentTerminators() throws IOException {
        byte terminator = (byte) '\n';
        ByteArraySingleTerminatorSerializer serializer = new ByteArraySingleTerminatorSerializer(terminator);
        ByteArrayInputStream inputStream = new ByteArrayInputStream("s\n\n".getBytes());

        try {
            byte[] bytes = serializer.deserialize(inputStream);
            assertEquals(1, bytes.length);
            assertEquals("s".getBytes()[0], bytes[0]);
            bytes = serializer.deserialize(inputStream);
            assertEquals(0, bytes.length);
        }
        finally {
            inputStream.close();
        }
    }

}
