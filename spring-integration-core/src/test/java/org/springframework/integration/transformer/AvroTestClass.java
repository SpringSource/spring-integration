/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package org.springframework.integration.transformer;

import org.apache.avro.specific.SpecificData;
import org.apache.avro.message.BinaryMessageEncoder;
import org.apache.avro.message.BinaryMessageDecoder;
import org.apache.avro.message.SchemaStore;

@SuppressWarnings("all")
@org.apache.avro.specific.AvroGenerated
public class AvroTestClass extends org.apache.avro.specific.SpecificRecordBase implements org.apache.avro.specific.SpecificRecord {
  private static final long serialVersionUID = 389393217217690928L;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"record\",\"name\":\"AvroTestClass\",\"namespace\":\"org.springframework.integration.transformer\",\"fields\":[{\"name\":\"bar\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}]},{\"name\":\"qux\",\"type\":[\"null\",{\"type\":\"string\",\"avro.java.string\":\"String\"}],\"default\":null}]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  private static SpecificData MODEL$ = new SpecificData();

  private static final BinaryMessageEncoder<AvroTestClass> ENCODER =
      new BinaryMessageEncoder<AvroTestClass>(MODEL$, SCHEMA$);

  private static final BinaryMessageDecoder<AvroTestClass> DECODER =
      new BinaryMessageDecoder<AvroTestClass>(MODEL$, SCHEMA$);

  /**
   * Return the BinaryMessageDecoder instance used by this class.
   */
  public static BinaryMessageDecoder<AvroTestClass> getDecoder() {
    return DECODER;
  }

  /**
   * Create a new BinaryMessageDecoder instance for this class that uses the specified {@link SchemaStore}.
   * @param resolver a {@link SchemaStore} used to find schemas by fingerprint
   */
  public static BinaryMessageDecoder<AvroTestClass> createDecoder(SchemaStore resolver) {
    return new BinaryMessageDecoder<AvroTestClass>(MODEL$, SCHEMA$, resolver);
  }

  /** Serializes this AvroTestClass to a ByteBuffer. */
  public java.nio.ByteBuffer toByteBuffer() throws java.io.IOException {
    return ENCODER.encode(this);
  }

  /** Deserializes a AvroTestClass from a ByteBuffer. */
  public static AvroTestClass fromByteBuffer(
      java.nio.ByteBuffer b) throws java.io.IOException {
    return DECODER.decode(b);
  }

  @Deprecated public java.lang.String bar;
  @Deprecated public java.lang.String qux;

  /**
   * Default constructor.  Note that this does not initialize fields
   * to their default values from the schema.  If that is desired then
   * one should use <code>newBuilder()</code>.
   */
  public AvroTestClass() {}

  /**
   * All-args constructor.
   * @param bar The new value for bar
   * @param qux The new value for qux
   */
  public AvroTestClass(java.lang.String bar, java.lang.String qux) {
    this.bar = bar;
    this.qux = qux;
  }

  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
  // Used by DatumWriter.  Applications should not call.
  public java.lang.Object get(int field$) {
    switch (field$) {
    case 0: return bar;
    case 1: return qux;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  // Used by DatumReader.  Applications should not call.
  @SuppressWarnings(value="unchecked")
  public void put(int field$, java.lang.Object value$) {
    switch (field$) {
    case 0: bar = (java.lang.String)value$; break;
    case 1: qux = (java.lang.String)value$; break;
    default: throw new org.apache.avro.AvroRuntimeException("Bad index");
    }
  }

  /**
   * Gets the value of the 'bar' field.
   * @return The value of the 'bar' field.
   */
  public java.lang.String getBar() {
    return bar;
  }

  /**
   * Sets the value of the 'bar' field.
   * @param value the value to set.
   */
  public void setBar(java.lang.String value) {
    this.bar = value;
  }

  /**
   * Gets the value of the 'qux' field.
   * @return The value of the 'qux' field.
   */
  public java.lang.String getQux() {
    return qux;
  }

  /**
   * Sets the value of the 'qux' field.
   * @param value the value to set.
   */
  public void setQux(java.lang.String value) {
    this.qux = value;
  }

  /**
   * Creates a new AvroTestClass RecordBuilder.
   * @return A new AvroTestClass RecordBuilder
   */
  public static org.springframework.integration.transformer.AvroTestClass.Builder newBuilder() {
    return new org.springframework.integration.transformer.AvroTestClass.Builder();
  }

  /**
   * Creates a new AvroTestClass RecordBuilder by copying an existing Builder.
   * @param other The existing builder to copy.
   * @return A new AvroTestClass RecordBuilder
   */
  public static org.springframework.integration.transformer.AvroTestClass.Builder newBuilder(org.springframework.integration.transformer.AvroTestClass.Builder other) {
    return new org.springframework.integration.transformer.AvroTestClass.Builder(other);
  }

  /**
   * Creates a new AvroTestClass RecordBuilder by copying an existing AvroTestClass instance.
   * @param other The existing instance to copy.
   * @return A new AvroTestClass RecordBuilder
   */
  public static org.springframework.integration.transformer.AvroTestClass.Builder newBuilder(org.springframework.integration.transformer.AvroTestClass other) {
    return new org.springframework.integration.transformer.AvroTestClass.Builder(other);
  }

  /**
   * RecordBuilder for AvroTestClass instances.
   */
  public static class Builder extends org.apache.avro.specific.SpecificRecordBuilderBase<AvroTestClass>
    implements org.apache.avro.data.RecordBuilder<AvroTestClass> {

    private java.lang.String bar;
    private java.lang.String qux;

    /** Creates a new Builder */
    private Builder() {
      super(SCHEMA$);
    }

    /**
     * Creates a Builder by copying an existing Builder.
     * @param other The existing Builder to copy.
     */
    private Builder(org.springframework.integration.transformer.AvroTestClass.Builder other) {
      super(other);
      if (isValidValue(fields()[0], other.bar)) {
        this.bar = data().deepCopy(fields()[0].schema(), other.bar);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.qux)) {
        this.qux = data().deepCopy(fields()[1].schema(), other.qux);
        fieldSetFlags()[1] = true;
      }
    }

    /**
     * Creates a Builder by copying an existing AvroTestClass instance
     * @param other The existing instance to copy.
     */
    private Builder(org.springframework.integration.transformer.AvroTestClass other) {
            super(SCHEMA$);
      if (isValidValue(fields()[0], other.bar)) {
        this.bar = data().deepCopy(fields()[0].schema(), other.bar);
        fieldSetFlags()[0] = true;
      }
      if (isValidValue(fields()[1], other.qux)) {
        this.qux = data().deepCopy(fields()[1].schema(), other.qux);
        fieldSetFlags()[1] = true;
      }
    }

    /**
      * Gets the value of the 'bar' field.
      * @return The value.
      */
    public java.lang.String getBar() {
      return bar;
    }

    /**
      * Sets the value of the 'bar' field.
      * @param value The value of 'bar'.
      * @return This builder.
      */
    public org.springframework.integration.transformer.AvroTestClass.Builder setBar(java.lang.String value) {
      validate(fields()[0], value);
      this.bar = value;
      fieldSetFlags()[0] = true;
      return this;
    }

    /**
      * Checks whether the 'bar' field has been set.
      * @return True if the 'bar' field has been set, false otherwise.
      */
    public boolean hasBar() {
      return fieldSetFlags()[0];
    }


    /**
      * Clears the value of the 'bar' field.
      * @return This builder.
      */
    public org.springframework.integration.transformer.AvroTestClass.Builder clearBar() {
      bar = null;
      fieldSetFlags()[0] = false;
      return this;
    }

    /**
      * Gets the value of the 'qux' field.
      * @return The value.
      */
    public java.lang.String getQux() {
      return qux;
    }

    /**
      * Sets the value of the 'qux' field.
      * @param value The value of 'qux'.
      * @return This builder.
      */
    public org.springframework.integration.transformer.AvroTestClass.Builder setQux(java.lang.String value) {
      validate(fields()[1], value);
      this.qux = value;
      fieldSetFlags()[1] = true;
      return this;
    }

    /**
      * Checks whether the 'qux' field has been set.
      * @return True if the 'qux' field has been set, false otherwise.
      */
    public boolean hasQux() {
      return fieldSetFlags()[1];
    }


    /**
      * Clears the value of the 'qux' field.
      * @return This builder.
      */
    public org.springframework.integration.transformer.AvroTestClass.Builder clearQux() {
      qux = null;
      fieldSetFlags()[1] = false;
      return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AvroTestClass build() {
      try {
        AvroTestClass record = new AvroTestClass();
        record.bar = fieldSetFlags()[0] ? this.bar : (java.lang.String) defaultValue(fields()[0]);
        record.qux = fieldSetFlags()[1] ? this.qux : (java.lang.String) defaultValue(fields()[1]);
        return record;
      } catch (java.lang.Exception e) {
        throw new org.apache.avro.AvroRuntimeException(e);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumWriter<AvroTestClass>
    WRITER$ = (org.apache.avro.io.DatumWriter<AvroTestClass>)MODEL$.createDatumWriter(SCHEMA$);

  @Override public void writeExternal(java.io.ObjectOutput out)
    throws java.io.IOException {
    WRITER$.write(this, SpecificData.getEncoder(out));
  }

  @SuppressWarnings("unchecked")
  private static final org.apache.avro.io.DatumReader<AvroTestClass>
    READER$ = (org.apache.avro.io.DatumReader<AvroTestClass>)MODEL$.createDatumReader(SCHEMA$);

  @Override public void readExternal(java.io.ObjectInput in)
    throws java.io.IOException {
    READER$.read(this, SpecificData.getDecoder(in));
  }

}