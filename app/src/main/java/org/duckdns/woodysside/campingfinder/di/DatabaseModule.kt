package org.duckdns.woodysside.campingfinder.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.duckdns.woodysside.campingfinder.data.local.CampingFinderDatabase
import org.duckdns.woodysside.campingfinder.data.local.CampsiteCacheDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CampingFinderDatabase =
        Room.databaseBuilder(context, CampingFinderDatabase::class.java, CampingFinderDatabase.NAME)
            // ★ 마이그레이션을 쓰지 않는다.
            //
            // 이 DB 는 서버 응답의 파생본이다. 스키마가 바뀌면 통째로 버리고 다시 받으면 된다.
            // 백엔드에서 Supabase 를 공공데이터의 파생본으로 보고 EC2 를 무상태로 만든 것과 같은 판단이다.
            // 되돌릴 수 있는 데이터에 마이그레이션 코드를 쓰는 것은 유지보수 부채만 만든다.
            //
            // ⚠️ 즐겨찾기가 들어오는 순간 이 줄을 지워야 한다. 즐겨찾기는 파생본이 아니라
            //    사용자가 만든 원본이다. 앱 업데이트 한 번에 조용히 사라지면 그건 데이터 손실이다.
            //    그래서 ksp 에 room.schemaLocation 을 미리 걸어 뒀다 (app/build.gradle.kts).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideCampsiteCacheDao(database: CampingFinderDatabase): CampsiteCacheDao =
        database.campsiteCacheDao()
}
