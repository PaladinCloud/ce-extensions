package com.paladincloud.assetstate;

import com.paladincloud.common.assets.AssetTypesHelper;
import com.paladincloud.common.aws.AssetStorageHelper;
import com.paladincloud.common.aws.SQSHelper;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class ApplicationModule {

    @Singleton
    @Provides
    AssetStorageHelper provideStorageHelper() { return new AssetStorageHelper(); }

    @Singleton
    @Provides
    AssetTypesHelper provideAssetTypesHelper() { return new AssetTypesHelper(); }

    @Singleton
    @Provides
    SQSHelper provideSQSHelper() { return new SQSHelper(); }
}
