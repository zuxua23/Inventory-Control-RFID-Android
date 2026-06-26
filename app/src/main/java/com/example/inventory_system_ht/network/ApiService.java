package com.example.inventory_system_ht.network;

import com.example.inventory_system_ht.model.AuthResponses;
import com.example.inventory_system_ht.model.AvailableTagResponses;
import com.example.inventory_system_ht.model.DeliveryOrderResponses;
import com.example.inventory_system_ht.model.GeneralResponse;
import com.example.inventory_system_ht.model.ItemResponses;
import com.example.inventory_system_ht.model.LocationResponses;
import com.example.inventory_system_ht.model.StockInRequest;
import com.example.inventory_system_ht.model.StockPrepBulkRequest;
import com.example.inventory_system_ht.model.StockTakingResponses;
import com.example.inventory_system_ht.model.TagResponses;

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
    Call<AuthResponses.LoginResponse> login(@Body AuthResponses.LoginRequest loginRequest);

    @POST("api/tag/register")
    Call<GeneralResponse> registerTags(@Header("Authorization") String token,
                                       @Body AuthResponses.RegisterRequest request);

    @POST("api/tag/register-with-item")
    Call<GeneralResponse> registerTagWithItem(@Header("Authorization") String token,
                                              @Body TagResponses.RegisterWithItemReq request);

    @GET("api/tag/{id}")
    Call<TagResponses.TagDetailDto> getTagDetail(@Header("Authorization") String token,
                                                 @Path("id") String tagId);

    @GET("api/stockin")
    Call<TagResponses.TagResponse> getTagByCode(@Header("Authorization") String token,
                                                @Query("code") String code,
                                                @Query("scannerType") String scannerType);

    @GET("api/stockin")
    Call<TagResponses.TagInfoDto> getTagInfo(@Header("Authorization") String token,
                                             @Query("code") String code,
                                             @Query("scannerType") String scannerType);

    @POST("api/stockin")
    Call<GeneralResponse> stockIn(@Header("Authorization") String token,
                                  @Body StockInRequest request);

    @POST("api/stockin/bulk-info")
    Call<List<TagResponses.TagResponse>> getStockInTagsInfoBulk(@Header("Authorization") String token,
                                                                @Body TagResponses.BulkInfoReq request);

    @POST("api/preparation/bulk")
    Call<GeneralResponse> submitStockPrep(@Header("Authorization") String token,
                                          @Body StockPrepBulkRequest request);

    @GET("api/preparation/do")
    Call<List<DeliveryOrderResponses.DOResponse>> getDo(@Header("Authorization") String token);

    @GET("api/pickinglist/{id}")
    Call<DeliveryOrderResponses.DOResponse> getPickingListById(@Header("Authorization") String token,
                                                               @Path("id") String id);

    @GET("api/do")
    Call<List<DeliveryOrderResponses.DOResponse>> getAllDO(@Header("Authorization") String token);

    @GET("api/preparation/do/{id}")
    Call<DeliveryOrderResponses.DOResponse> getDoDetailForPrep(@Header("Authorization") String token,
                                                               @Path("id") String id);

    @POST("api/preparation/bulk-info")
    Call<List<TagResponses.TagInfoDto>> getTagsInfoBulk(@Header("Authorization") String token,
                                                        @Body TagResponses.PrepBulkInfoReq request);

    @POST("api/preparation/bulk-info")
    Call<List<TagResponses.TagInfoDto>> getTagsRegistBulk(@Header("Authorization") String token,
                                                          @Body TagResponses.BulkInfoReq request);

    @GET("api/preparation/available-tags/{doId}")
    Call<List<AvailableTagResponses>> getAvailableTagsForDo(@Header("Authorization") String token,
                                                            @Path("doId") String doId);

    @POST("api/tag/validate-epc")
    Call<List<TagResponses.TagInfoDto>> validateTagEpc(@Header("Authorization") String token,
                                                       @Body TagResponses.BulkInfoReq request);

    @GET("api/stock-taking/active")
    Call<StockTakingResponses.ActiveRes> getActiveStockTaking(@Header("Authorization") String token);

    @GET("api/stock-taking/tags/{sttId}")
    Call<List<StockTakingResponses.SessionItem>> getSessionTags(@Header("Authorization") String token,
                                                                @Path("sttId") String sttId);

    @GET("api/stock-taking/available-tags/{sttId}")
    Call<List<StockTakingResponses.AvailableTag>> getAvailableTags(
            @Header("Authorization") String token,
            @Path("sttId") String sttId
    );

    @GET("api/stock-taking/validate-tag")
    Call<StockTakingResponses.ValidateTagResult> validateManualTag(
            @Header("Authorization") String token,
            @Query("epc") String epc,
            @Query("sttId") String sttId
    );

    @POST("api/stock-taking/operator-submit")
    Call<GeneralResponse> operatorSubmit(@Header("Authorization") String token,
                                         @Body StockTakingResponses.OperatorSubmitReq request);

    @GET("api/location")
    Call<List<LocationResponses>> getLocations(@Header("Authorization") String token);

    @GET("api/item")
    Call<List<ItemResponses.ItemResponse>> getAllItems(@Header("Authorization") String token);

    @GET("api/search-item")
    Call<List<TagResponses.SearchItemDto>> getSearchItems(@Header("Authorization") String token);

    @GET("api/search-item/{code}")
    Call<TagResponses.TagDetailDto> getTagDetailSearchItem(@Header("Authorization") String token,
                                                           @Path("code") String code);
}