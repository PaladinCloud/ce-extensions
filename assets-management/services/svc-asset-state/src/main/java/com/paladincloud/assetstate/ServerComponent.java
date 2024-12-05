package com.paladincloud.assetstate;

import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = {ApplicationModule.class})
public interface ServerComponent {
    AssetStateJob buildAssetStateJob();
}
