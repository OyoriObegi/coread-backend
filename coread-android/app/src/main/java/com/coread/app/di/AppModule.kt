package com.coread.app.di
import android.content.Context
import androidx.room.Room
import com.coread.app.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class) object AppModule {
 @Provides @Singleton fun database(@ApplicationContext context:Context):CoReadDatabase=Room.databaseBuilder(context,CoReadDatabase::class.java,"coread.db").build()
}
