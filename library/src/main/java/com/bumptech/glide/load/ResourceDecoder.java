package com.bumptech.glide.load;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bumptech.glide.load.engine.Resource;
import java.io.IOException;

/**
 * 将ModelLoader加载出来的数据，进行解码，解码成Bitmap，或者BitmapDrawable之类的
 *
 * Glide中常用的Decoder有两个，其他都是将这两个Decoder进行包装，它们分别是ByteBufferBitmapDecoder和StreamBitmapDecoder
 *
 * 与 Encoder 对应，数据解码器，用来将原始数据解码成相应的数据类型，针对不同的请求实现类都不同，例如通过网络请求最终获取到的是一个 InputStream，经过 ByteBufferBitmapDecoder 解码后再生成一个 Bitmap
 *
 * 解码时会根据 option 以及图片大小（如果有的话）按需加载 Bitmap
 *
 * 图片数据获取到之后会根据不同的类型使用对应的解码器对其解码
 *
 * An interface for decoding resources.
 *
 * @param <T> The type the resource will be decoded from (File, InputStream etc).
 * @param <Z> The type of the decoded resource (Bitmap, Drawable etc).
 */
public interface ResourceDecoder<T, Z> {

  /**
   * Returns {@code true} if this decoder is capable of decoding the given source with the given
   * options, and {@code false} otherwise.
   *
   * <p>Decoders should make a best effort attempt to quickly determine if they are likely to be
   * able to decode data, but should not attempt to completely read the given data. A typical
   * implementation would check the file headers verify they match content the decoder expects to
   * handle (i.e. a GIF decoder should verify that the image contains the GIF header block.）
   *
   * <p>Decoders that return {@code true} from {@code handles} may still return {@code null} from
   * {@link #decode(Object, int, int, Options)} if the data is partial or formatted incorrectly.
   */
  boolean handles(@NonNull T source, @NonNull Options options) throws IOException;

  /**
   * Returns a decoded resource from the given data or null if no resource could be decoded.
   *
   * <p>The {@code source} is managed by the caller, there's no need to close it. The returned
   * {@link Resource} will be {@link Resource#recycle() released} when the engine sees fit.
   *
   * <p>Note - The {@code width} and {@code height} arguments are hints only, there is no
   * requirement that the decoded resource exactly match the given dimensions. A typical use case
   * would be to use the target dimensions to determine how much to downsample Bitmaps by to avoid
   * overly large allocations.
   *
   * @param source The data the resource should be decoded from.
   * @param width The ideal width in pixels of the decoded resource, or {@link
   *     com.bumptech.glide.request.target.Target#SIZE_ORIGINAL} to indicate the original resource
   *     width.
   * @param height The ideal height in pixels of the decoded resource, or {@link
   *     com.bumptech.glide.request.target.Target#SIZE_ORIGINAL} to indicate the original resource
   *     height.
   * @param options A map of string keys to objects that may or may not contain options available to
   *     this particular implementation. Implementations should not assume that any or all of their
   *     option keys are present. However, implementations may assume that if one of their option
   *     keys is present, it's value is non-null and is of the expected type.
   */
  @Nullable
  Resource<Z> decode(@NonNull T source, int width, int height, @NonNull Options options)
      throws IOException;
}
