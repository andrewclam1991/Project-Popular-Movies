package com.andrewclam.popularmovie.data.source;

import android.accounts.NetworkErrorException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.andrewclam.popularmovie.data.DataSource;
import com.andrewclam.popularmovie.data.model.ApiResponse;
import com.andrewclam.popularmovie.data.model.Entity;
import com.google.common.base.Optional;

import java.util.List;
import java.util.Map;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static com.google.common.base.Preconditions.checkNotNull;

@Deprecated
public abstract class ApiServiceDecorator<E extends Entity> implements DataSource<E> {

  @NonNull
  private final DataSource<E> mRepository;

  @Nullable
  private Map<String, String> mOptions;

  @VisibleForTesting
  boolean mCacheIsDirty = false;

  ApiServiceDecorator(@NonNull DataSource<E> repository) {
    checkNotNull(repository, "target repository can't be null!");
    mRepository = repository;
  }

  /**
   * add a behavior that allows client to supply optional query options
   *
   * @param callback when client sets a query option via a functional callback
   *                 this allows client to vary argument at runtime instead of supplying
   *                 static arguments.
   * @return an instance of the wrapper class to maintain api fluency.
   */
  @NonNull
  public ApiServiceDecorator<E> setQueryParams(@NonNull OnRequestOptionsCallback callback) {
    mOptions = callback.provideOptions();
    return this;
  }

  /**
   * Functional interface for client to provide the optional query parameters (options)
   * with a {@link Map<>}, where the key is the query parameter and the value
   * is the argument.
   */
  public interface OnRequestOptionsCallback {
    @Nullable
    Map<String, String> provideOptions();
  }

  // Decorated behaviors
  @NonNull
  @Override
  public final Flowable<List<E>> getItems() {
    // when cache is dirty, use "getItems(uri)" behavior
    if (mCacheIsDirty) {
      return getItemsFromServiceApi();
    } else {
      // else, use repository first, use "getItems(uri)" behavior ONLY when first stream is empty
      return mRepository.getItems().switchIfEmpty(getItemsFromServiceApi());
    }
  }

  @Override
  public final void refresh() {
    mCacheIsDirty = true;
    mRepository.refresh();
  }

  /**
   * Uses {@link retrofit2.Retrofit} to make the generic call to the {@code apiService}
   *
   * @return an observable list of items
   */
  private Flowable<List<E>> getItemsFromServiceApi() {

    return Flowable.create(emitter -> {
      provideApiServiceCall(mOptions)
          .enqueue(new Callback<ApiResponse<E>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<E>> call,
                                   @NonNull Response<ApiResponse<E>> response) {
              String logmsg = response.raw().toString();

              ApiResponse<E> apiResponse = response.body();
              if (apiResponse == null) {
                emitter.onError(new NetworkErrorException(
                    "Response body returns empty, call: " + logmsg));
              } else {
                List<E> items = apiResponse.getResults();
                emitter.onNext(items);
                emitter.onComplete();
              }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<E>> call, @NonNull Throwable t) {
              emitter.onError(t);
            }
          });
    }, BackpressureStrategy.BUFFER);

  }

  @NonNull
  public abstract Call<ApiResponse<E>> provideApiServiceCall(@Nullable Map<String, String> options);

  // Unmodified behaviors
  @NonNull
  @Override
  public final Flowable<Optional<E>> getItem(@NonNull String entityId) {
    return mRepository.getItem(entityId);
  }

  @NonNull
  @Override
  public final Completable add(@NonNull E item) {
    return mRepository.add(item);
  }

  @NonNull
  @Override
  public final Completable addAll(@NonNull List<E> items) {
    return mRepository.addAll(items);
  }

  @NonNull
  @Override
  public final Completable update(@NonNull E item) {
    return mRepository.update(item);
  }

  @NonNull
  @Override
  public final Completable remove(@NonNull String entityId) {
    return mRepository.remove(entityId);
  }

  @NonNull
  @Override
  public final Completable removeAll() {
    return mRepository.removeAll();
  }

  @NonNull
  @Override
  public Flowable<List<E>> getItems(@NonNull Map<String, String> options) {
    return mRepository.getItems(options);
  }
}