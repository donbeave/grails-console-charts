/*
 * Copyright 2014 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugin.console.charts.client.application;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.http.client.*;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.web.bindery.autobean.shared.AutoBeanCodex;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyStandard;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;
import grails.plugin.console.charts.client.application.share.AbstractSharePresenter;
import grails.plugin.console.charts.client.place.NameTokens;
import grails.plugin.console.charts.client.place.ParameterTokens;
import grails.plugin.console.charts.shared.ConnectStatus;
import grails.plugin.console.charts.shared.events.ConnectedEvent;
import grails.plugin.console.charts.shared.events.ConnectedHandler;

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
public class AbstractApplicationPresenter extends Presenter<AbstractApplicationPresenter.MyView,
        AbstractApplicationPresenter.MyProxy> implements ApplicationUiHandlers, ConnectedHandler {

    @ProxyStandard
    @NameToken(NameTokens.HOME)
    public interface MyProxy extends ProxyPlace<AbstractApplicationPresenter> {
    }

    public interface MyView extends View, HasUiHandlers<ApplicationUiHandlers> {

        void loading();

        void error(JSONObject result);

        void error(String error);

        void view(String type, JSONObject result);

    }

    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> TYPE_SetMainContent =
            new GwtEvent.Type<RevealContentHandler<?>>();

    public static final Object TOP_SLOT = new Object();
    public static final Object LEFT_SLOT = new Object();
    public static final String DEFAULT_VIEW = "Line";

    PlaceManager placeManager;

    private AbstractSharePresenter sharePresenter;

    JSONObject result;

    public AbstractApplicationPresenter(final EventBus eventBus,
                                        final MyView view,
                                        final MyProxy proxy,
                                        final RevealType slot,
                                        final PlaceManager placeManager,
                                        final AbstractSharePresenter sharePresenter) {
        super(eventBus, view, proxy, slot);

        this.placeManager = placeManager;
        this.sharePresenter = sharePresenter;

        view.setUiHandlers(this);
    }

    @Override
    public void prepareFromRequest(PlaceRequest request) {
        String query = request.getParameter(ParameterTokens.QUERY, null);
        String appearance = request.getParameter(ParameterTokens.APPEARANCE, null);
        String view = request.getParameter(ParameterTokens.VIEW, DEFAULT_VIEW);
        String connectionString = request.getParameter(ParameterTokens.CONNECTION_STRING, null);

        if (connectionString != null)
            AppUtils.CONNECTION_STRING = URL.decodePathSegment(connectionString);

        if (query != null)
            query = AppUtils.decodeBase64(URL.decodePathSegment(query));

        if (appearance != null)
            appearance = AppUtils.decodeBase64(URL.decodePathSegment(appearance));

        if (query != null && !query.equals(AppUtils.QUERY)) {
            AppUtils.QUERY = query;
            result = null;
        }

        if (appearance != null && !appearance.equals(AppUtils.APPEARANCE)) {
            AppUtils.APPEARANCE = appearance;
            result = null;
        }

        if (view != null && !view.equals(AppUtils.VIEW)) {
            AppUtils.VIEW = view;
        }
    }

    @Override
    public void onViewChanged(String view) {
        PlaceRequest request = new PlaceRequest.Builder().nameToken(NameTokens.HOME)
                .with(ParameterTokens.QUERY, AppUtils.encodeBase64(URL.encodePathSegment(AppUtils.QUERY)))
                .with(ParameterTokens.CONNECTION_STRING, URL.encodePathSegment(AppUtils.CONNECTION_STRING))
                .with(ParameterTokens.VIEW, view).build();

        placeManager.revealPlace(request);
    }

    @Override
    public void onShareClicked() {
        addToPopupSlot(sharePresenter);
    }

    @Override
    public void onConnected(ConnectedEvent event) {
        AppUtils.CONNECTION_STRING = event.getConnectionString();
    }

    @Override
    protected void onBind() {
        addRegisteredHandler(ConnectedEvent.getType(), this);
    }

    @Override
    protected void onReset() {
        if (result == null) {
            if (AppUtils.QUERY != null) {
                if (AppUtils.CONNECTION_STRING == null) {
                    getView().error("Not connected to server");

                    return;
                }

                if (AppUtils.CONNECT_STATUS == null) {
                    try {
                        String url = AppUtils.getConnectPath() + "?data=" + URL.encodePathSegment(AppUtils.CONNECTION_STRING);
                        RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, url);

                        rb.setCallback(new RequestCallback() {
                            @Override
                            public void onResponseReceived(Request request, Response response) {
                                ConnectStatus status = AutoBeanCodex.decode(AppUtils.BEAN_FACTORY, ConnectStatus.class,
                                        response.getText()).as();

                                if (status.isConnected()) {
                                    AppUtils.CONNECT_STATUS = status;

                                    ConnectedEvent.fire(AbstractApplicationPresenter.this, status.getConnectionString(),
                                            status.getStatus());

                                    loadData();
                                } else {
                                    String error =
                                            (status.getException() != null ? " (" + status.getException() + ") " : "")
                                                    + status.getError();
                                    Window.alert("Error occurred: " + error);
                                }
                            }

                            @Override
                            public void onError(Request request, Throwable exception) {
                                Window.alert("Error occurred: " + exception.getMessage());
                            }
                        });

                        rb.send();
                    } catch (RequestException e) {
                        Window.alert("Error occurred: " + e.getMessage());
                    }
                } else {
                    loadData();
                }
            }
        } else {
            getView().view(AppUtils.VIEW, result);
        }
    }

    private void loadData() {
        getView().loading();

        try {
            RequestBuilder rb = new RequestBuilder(RequestBuilder.GET, AppUtils.getDataPath() +
                    "?query=" + URL.encodeQueryString(AppUtils.encodeBase64(AppUtils.QUERY)) +
                    "&appearance=" + URL.encodePathSegment(AppUtils.encodeBase64(AppUtils.APPEARANCE)) +
                    "&connectionString=" + URL.encodePathSegment(AppUtils.CONNECTION_STRING));

            rb.setCallback(new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    try {
                        JSONValue value = JSONParser.parseStrict(response.getText());
                        result = value.isObject();

                        if (result.get("error") != null) {
                            getView().error(result);
                            return;
                        }

                        getView().view(AppUtils.VIEW, result);
                    } catch (Exception exception) {
                        getView().error("Can't parse data JSON: " + exception.getMessage());
                    } finally {
                        result = null;
                    }
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    getView().error("Error occurred: " + exception.getMessage());
                }
            });

            rb.send();
        } catch (RequestException e) {
            getView().error("Error occurred: " + e.getMessage());
        }
    }

}