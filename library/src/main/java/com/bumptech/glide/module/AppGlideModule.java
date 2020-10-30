package com.bumptech.glide.module;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.GlideBuilder;

/**
 * Defines a set of dependencies and options to use when initializing Glide within an application.
 *
 * <p>There can be at most one {@link AppGlideModule} in an application. Only Applications can
 * include a {@link AppGlideModule}. Libraries must use {@link LibraryGlideModule}.
 *
 * <p>Classes that extend {@link AppGlideModule} must be annotated with {@link
 * com.bumptech.glide.annotation.GlideModule} to be processed correctly.
 *
 * <p>Classes that extend {@link AppGlideModule} can optionally be annotated with {@link
 * com.bumptech.glide.annotation.Excludes} to optionally exclude one or more {@link
 * LibraryGlideModule} and/or {@link GlideModule} classes.
 *
 * <p>Once an application has migrated itself and all libraries it depends on to use Glide's
 * annotation processor, {@link AppGlideModule} implementations should override {@link
 * #isManifestParsingEnabled()} and return {@code false}.
 */
// Used only in javadoc.
@SuppressWarnings("deprecation")
public abstract class AppGlideModule extends LibraryGlideModule implements AppliesOptions {
  /**
   * Returns {@code true} if Glide should check the AndroidManifest for {@link GlideModule}s.
   *
   * <p>Implementations should return {@code false} after they and their dependencies have migrated
   * to Glide's annotation processor.
   *
   * <p>Returns {@code true} by default.
   *
   * 因为glide V4是兼容V3版本的所以他还会从manifast中读取GlideModule信息,但是呢,我们已经把manifast
   * 的GlideModule已经去掉了,为了保证咱们初始化glide的效率,这个方法就是不让glide从manifast中读取了
   * ,从而达到高效初始化的效果
   */
  public boolean isManifestParsingEnabled() {
    return true;
  }

  @Override
  public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
    // Default empty impl.
  }
}
