package redis;

import com.google.common.base.Charsets;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Command serialization.
 * User: sam
 * Date: 7/27/11
 * Time: 3:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class Command {
  public static final byte[] ARGS_PREFIX = "*".getBytes();
  public static final byte[] CRLF = "\r\n".getBytes();
  public static final byte[] BYTES_PREFIX = "$".getBytes();
  public static final byte[] EMPTY_BYTES = new byte[0];
  public static final byte[] NEG_ONE = convert(-1, false);
  public static final byte[] NEG_ONE_WITH_CRLF = convert(-1, true);
  public static final char LF = '\n';
  public static final char CR = '\r';

  private final Object name;
  private final Object[] objects;
  private final Object object1;
  private final Object object2;
  private final Object object3;

  public Command(Object[] objects) {
    this(null, null, null, null, objects);
  }

  public Command(Object name) {
    this(name, null, null, null, null);
  }

  public Command(Object name, Object[] objects) {
    this(name, null, null, null, objects);
  }

  public Command(Object name, Object object1) {
    this(name, object1, null, null, null);
  }

  public Command(Object name, Object object1, Object object2) {
    this(name, object1, object2, null, null);
  }

  public Command(Object name, Object object1, Object object2, Object object3) {
    this(name, object1, object2, object3, null);
  }

  private Command(Object name, Object object1, Object object2, Object object3, Object[] objects) {
    this.name = name;
    this.object1 = object1;
    this.object2 = object2;
    this.object3 = object3;
    this.objects = objects;
  }

  public void write(ChannelBuffer os) throws IOException {
    writeDirect(os, name, object1, object2, object3, objects);
  }

  public static void writeDirect(ChannelBuffer os, Object name, Object object1, Object object2, Object object3, Object[] objects) throws IOException {
    int others = (object1 == null ? 0 : 1) + (object2 == null ? 0 : 1) +
            (object3 == null ? 0 : 1) + (name == null ? 0 : 1);
    int length = objects == null ? 0 : objects.length;
    os.writeBytes(ARGS_PREFIX);
    os.writeBytes(Command.numToBytes(length + others, true));
    if (name != null) writeObject(os, name);
    if (object1 != null) writeObject(os, object1);
    if (object2 != null) writeObject(os, object2);
    if (object3 != null) writeObject(os, object3);
    if (objects != null) {
      for (Object object : objects) {
        writeObject(os, object);
      }
    }
  }

  private static void writeObject(ChannelBuffer os, Object object) throws IOException {
    byte[] argument;
    if (object == null) {
      argument = EMPTY_BYTES;
    } else if (object instanceof ChannelBuffer) {
      writeArgument(os, (ChannelBuffer) object);
      return;
    } else if (object instanceof byte[]) {
      argument = (byte[]) object;
    } else {
      argument = object.toString().getBytes(Charsets.UTF_8);
    }
    writeArgument(os, argument);
  }

  private static void writeArgument(ChannelBuffer os, byte[] argument) throws IOException {
    os.writeBytes(BYTES_PREFIX);
    os.writeBytes(Command.numToBytes(argument.length, true));
    os.writeBytes(argument);
    os.writeBytes(CRLF);
  }

  private static void writeArgument(ChannelBuffer os, ChannelBuffer argument) throws IOException {
    os.writeBytes(BYTES_PREFIX);
    os.writeBytes(Command.numToBytes(argument.capacity(), true));
    os.writeBytes(argument);
    os.writeBytes(CRLF);
  }

  private static final int NUM_MAP_LENGTH = 256;
  private static byte[][] numMap = new byte[NUM_MAP_LENGTH][];

  static {
    for (int i = 0; i < NUM_MAP_LENGTH; i++) {
      numMap[i] = convert(i, false);
    }
  }

  private static byte[][] numMapWithCRLF = new byte[NUM_MAP_LENGTH][];

  static {
    for (int i = 0; i < NUM_MAP_LENGTH; i++) {
      numMapWithCRLF[i] = convert(i, true);
    }
  }

  // Optimized for the direct to ASCII bytes case
  // Could be even more optimized but it is already
  // about twice as fast as using Long.toString().getBytes()
  public static byte[] numToBytes(long value, boolean withCRLF) {
    if (value >= 0 && value < NUM_MAP_LENGTH) {
      int index = (int) value;
      return withCRLF ? numMapWithCRLF[index] : numMap[index];
    } else if (value == -1) {
      return withCRLF ? NEG_ONE_WITH_CRLF : NEG_ONE;
    }
    return convert(value, withCRLF);
  }

  private static byte[] convert(long value, boolean withCRLF) {
    boolean negative = value < 0;
    int index = negative ? 2 : 1;
    long current = negative ? -value : value;
    while ((current /= 10) > 0) {
      index++;
    }
    byte[] bytes = new byte[withCRLF ? index + 2 : index];
    if (withCRLF) {
      bytes[index + 1] = LF;
      bytes[index] = CR;
    }
    if (negative) {
      bytes[0] = '-';
    }
    current = negative ? -value : value;
    long tmp = current;
    while ((tmp /= 10) > 0) {
      bytes[--index] = (byte) ('0' + (current % 10));
      current = tmp;
    }
    bytes[--index] = (byte) ('0' + current);
    return bytes;
  }
}
