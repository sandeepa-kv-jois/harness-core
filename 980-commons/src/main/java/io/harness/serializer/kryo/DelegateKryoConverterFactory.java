/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static java.util.Arrays.stream;

import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Converter.Factory;
import retrofit2.Retrofit;

@Singleton
@Slf4j
public class DelegateKryoConverterFactory extends Factory {
  private static final MediaType MEDIA_TYPE = MediaType.parse("application/x-kryo-v2");

  private final KryoSerializer kryoSerializer;

  @Inject
  public DelegateKryoConverterFactory(@Named("referenceFalseKryoSerializer") final KryoSerializer kryoSerializer) {
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public Converter<?, RequestBody> requestBodyConverter(final Type type, final Annotation[] parameterAnnotations,
      final Annotation[] methodAnnotations, final Retrofit retrofit) {
    if (stream(methodAnnotations)
            .anyMatch(annotation -> annotation.annotationType().isAssignableFrom(KryoRequest.class))) {
      return value -> RequestBody.create(MEDIA_TYPE, kryoSerializer.asBytes(value));
    }
    return null;
  }

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(
      final Type type, final Annotation[] annotations, final Retrofit retrofit) {
    if (stream(annotations).anyMatch(annotation -> annotation.annotationType().isAssignableFrom(KryoResponse.class))) {
      return value -> {
        try (value) {
          return kryoSerializer.asObject(value.bytes());
        } catch (final Exception e) {
          log.error("Exception deserializing object of type {}", type, e);
          throw e;
        }
      };
    }
    return null;
  }
}
