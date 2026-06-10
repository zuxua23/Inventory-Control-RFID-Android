package com.example.inventory_system_ht.network;

import com.example.inventory_system_ht.model.AuthModel;
import com.example.inventory_system_ht.model.DOModel;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.ItemModel;
import com.example.inventory_system_ht.model.LocationModel;
import com.example.inventory_system_ht.model.StockInRequest;
import com.example.inventory_system_ht.model.StockPrepBulkRequest;
import com.example.inventory_system_ht.model.StockTakingModel;
import com.example.inventory_system_ht.model.TagModel;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @GET("api/ping")
    Call<GeneralResponse> ping();

    @POST("api/auth/login")
    Call<AuthModel.LoginResponse> login(@Body AuthModel.LoginRequest loginRequest);

    @POST("api/tag/register")
    Call<GeneralResponse> registerTags(@Header("Authorization") String token,
                                       @Body AuthModel.RegisterRequest request);

    @GET("api/tag/{id}")
    Call<TagModel.TagDetailDto> getTagDetail(@Header("Authorization") String token,
                                             @Path("id") String tagId);

    @GET("api/stockin")
    Call<TagModel.TagResponse> getTagByCode(@Header("Authorization") String token,
                                            @Query("code") String code,
                                            @Query("scannerType") String scannerType);

    @GET("api/stockin")
    Call<TagModel.TagInfoDto> getTagInfo(@Header("Authorization") String token,
                                         @Query("code") String code,
                                         @Query("scannerType") String scannerType);

    @POST("api/stockin")
    Call<GeneralResponse> stockIn(@Header("Authorization") String token,
                                  @Body StockInRequest request);

    @POST("api/stockin/bulk-info")
    Call<List<TagModel.TagResponse>> getStockInTagsInfoBulk(@Header("Authorization") String token,
                                                            @Body TagModel.BulkInfoReq request);

    @POST("api/preparation/bulk")
    Call<GeneralResponse> submitStockPrep(@Header("Authorization") String token,
                                          @Body StockPrepBulkRequest request);

    @GET("api/preparation/do")
    Call<List<DOModel.DOResponse>> getDo(@Header("Authorization") String token);

    @GET("api/pickinglist/{id}")
    Call<DOModel.DOResponse> getPickingListById(@Header("Authorization") String token,
                                                @Path("id") String id);

    @GET("api/do")
    Call<List<DOModel.DOResponse>> getAllDO(@Header("Authorization") String token);

    @GET("api/preparation/do/{id}")
    Call<DOModel.DOResponse> getDoDetailForPrep(@Header("Authorization") String token,
                                                @Path("id") String id);

    @POST("api/preparation/bulk-info")
    Call<List<TagModel.TagInfoDto>> getTagsInfoBulk(@Header("Authorization") String token,
                                                    @Body TagModel.BulkInfoReq request);

    @GET("api/stock-taking/active")
    Call<StockTakingModel.ActiveRes> getActiveStockTaking(@Header("Authorization") String token);

    @GET("api/stock-taking/tags/{sttId}")
    Call<List<StockTakingModel.SessionItem>> getSessionTags(@Header("Authorization") String token,
                                                            @Path("sttId") String sttId);

    @GET("api/stock-taking/available-tags/{sttId}")
    Call<List<StockTakingModel.AvailableTag>> getAvailableTags(
            @Header("Authorization") String token,
            @Path("sttId") String sttId
    );

    @POST("api/stock-taking/operator-submit")
    Call<GeneralResponse> operatorSubmit(@Header("Authorization") String token,
                                         @Body StockTakingModel.OperatorSubmitReq request);

    @GET("api/location")
    Call<List<LocationModel>> getLocations(@Header("Authorization") String token);

    @GET("api/item")
    Call<List<ItemModel.ItemResponse>> getAllItems(@Header("Authorization") String token);

    @GET("api/search-item")
    Call<List<TagModel.SearchItemDto>> getSearchItems(@Header("Authorization") String token);

    @GET("api/search-item/{code}")
    Call<TagModel.TagDetailDto> getTagDetailSearchItem(@Header("Authorization") String token,
                                                       @Path("code") String code);
}
