package com.paladincloud.assetstate;

import com.paladincloud.common.aws.AssetStorageHelper;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class ApplicationModule {

    @Singleton
    @Provides
    AssetStorageHelper provideOpenSearchHelper() { return new AssetStorageHelper(); }
}
