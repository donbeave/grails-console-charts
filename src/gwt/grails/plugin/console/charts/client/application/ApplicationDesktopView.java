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

import com.dianaui.universal.core.client.ui.Alert;
import com.dianaui.universal.core.client.ui.Button;
import com.dianaui.universal.core.client.ui.FontAwesomeIcon;
import com.dianaui.universal.core.client.ui.constants.AlertType;
import com.dianaui.universal.core.client.ui.constants.IconSize;
import com.dianaui.universal.core.client.ui.constants.IconType;
import com.dianaui.universal.core.client.ui.html.Div;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.ScriptElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/**
 * @author <a href='mailto:donbeave@gmail.com'>Alexey Zhokhov</a>
 */
public class ApplicationDesktopView extends ViewWithUiHandlers<ApplicationUiHandlers>
        implements AbstractApplicationPresenter.MyView {

    static String CHART_ID = "chart";
    static String CHART_INIT_ID = "chartInit";

    interface Binder extends UiBinder<Widget, ApplicationDesktopView> {
    }

    @UiField(provided = true)
    SplitLayoutPanel container;

    @UiField
    LayoutPanel layoutContainer;

    @UiField
    Div topContainer;

    @UiField
    Div leftContainer;

    @UiField
    Div rightContainer;

    /*
    @UiField
    AnchorListItem lineChartButton;

    @UiField
    AnchorListItem barChartButton;

    @UiField
    AnchorListItem columnChartButton;

    @UiField
    AnchorListItem pieChartButton;
    */

    @UiField
    Button shareButton;

    @Inject
    ApplicationDesktopView(final Binder binder) {
        container = new SplitLayoutPanel(3);

        initWidget(binder.createAndBindUi(this));
    }

    @Override
    public void loading() {
        clear();

        FontAwesomeIcon loading = new FontAwesomeIcon();
        loading.getElement().setId("loading");
        loading.setType(IconType.REFRESH);
        loading.setSize(IconSize.LARGE);
        loading.setSpin(true);

        rightContainer.add(loading);
    }

    @Override
    public void error(JSONObject result) {
        if (result.get("text") != null) {
            error(result.get("exception").isString().stringValue() + ": " + result.get("text").isString().stringValue());
        } else {
            Window.alert("Unknown error!");
        }
    }

    @Override
    public void error(String error) {
        Alert alert = new Alert();
        alert.setType(AlertType.DANGER);
        alert.setText(error);

        clear();

        rightContainer.add(alert);
        rightContainer.setStyleName("error");
    }

    @Override
    public void view(String type, JSONObject result) {
        clear();

        DivElement div = Document.get().createDivElement();
        div.setId(CHART_ID);
        div.getStyle().setWidth(AppUtils.DEFAULT_WIDTH, Style.Unit.PX);
        div.getStyle().setProperty("margin", "0 auto");

        rightContainer.getElement().appendChild(div);

        Document.get().getElementById(CHART_ID).removeAllChildren();

        if (result.get("columns") == null) {
            Window.alert("Columns is empty!");
            return;
        }

        JSONArray columns = result.get("columns").isArray();
        JSONObject override = result.get("override") != null ? result.get("override").isObject() : null;

        String axes = "{\n" +
                "\"id\": \"v1\",\n" +
                "\"axisThickness\": 2,\n" +
                "\"gridAlpha\": 0,\n" +
                "\"axisAlpha\": 1,\n" +
                "\"position\": \"left\"\n" +
                "}";

        String graphs = "";

        for (int i = 1; columns.size() > i; i++) {
            String col = columns.get(i).toString();

            graphs += (graphs.equals("") ? "" : ",") + "{\n" +
                    "\"id\": \"g" + i + "\",\n" +
                    "\"balloonText\": \"" + (columns.size() > 2 ? "[[title]]: [[value]]" : "[[value]]") + "\",\n" +
                    "\"valueField\": " + col + ",\n" +
                    "\"valueAxis\": \"v" + i + "\",\n" +
                    "\"bullet\": \"round\",\n" +
                    "\"bulletBorderThickness\": 1,\n" +
                    "\"hideBulletsCount\": 30,\n" +
                    "\"title\": " + (override != null && override.get(col) != null && override.get(col).isString() != null ?
                    override.get(col) : col) + ",\n" +
                    "\"fillAlphas\": 0\n" +
                    "}";
        }

        String scrollbar = (columns.size() > 1) ?
                "{\n" +
                        "  \"autoGridCount\": true,\n" +
                        "  \"graph\": \"g1\",\n" +
                        "  \"scrollbarHeight\": 40\n" +
                        "}," : "{},";

        String cursor = (columns.size() > 5) ?
                "{\n" +
                        "  \"cursorAlpha\": 0.1,\n" +
                        "  \"cursorColor\": \"#000000\",\n" +
                        "  \"fullWidth\": true,\n" +
                        "  \"valueBalloonsEnabled\": false,\n" +
                        "  \"zoomable\": true\n" +
                        "}," : "{},";

        String content = "AmCharts.makeChart(\"" + CHART_ID + "\", {" +
                "\"type\": \"serial\",\n" +
                "\"theme\": \"none\",\n" +
                "\"pathToImages\": \"http://www.amcharts.com/lib/3/images/\",\n" +
                "\"legend\": {\n" +
                "  \"align\": \"center\",\n" +
                "  \"equalWidths\": true,\n" +
                "  \"periodValueText\": \"total: [[value.sum]]\",\n" +
                "  \"valueAlign\": \"left\",\n" +
                "  \"valueText\": \"[[value]] ([[percents]]%)\",\n" +
                "  \"valueWidth\": 100\n" +
                "},\n" +
                "\"dataDateFormat\": \"YYYY-MM-DD HH:NN\",\n" +
                "\"dataProvider\": " + result.get("content").toString() + ",\n" +
                "\"valueAxes\": [" + axes + "],\n" +
                "\"graphs\": [" + graphs + "],\n" +
                "\"chartScrollbar\": " + scrollbar + "\n" +
                "\"chartCursor\": " + cursor + "\n" +
                "\"categoryField\": " + columns.get(0) + ",\n" +
                "\"categoryAxis\": {\n" +
                "  \"parseDates\": true,\n" +
                "  \"axisColor\": \"#DADADA\",\n" +
                "  \"minorGridEnabled\": true\n" +
                "}\n" +
                "});";

        ScriptElement script = Document.get().createScriptElement(content);
        script.setId(CHART_INIT_ID);

        int height = ((columns.size() / 3) * 25) + 500;
        div.getStyle().setHeight(height, Style.Unit.PX);
        AppUtils.CURRENT_HEIGHT = height;

        rightContainer.getElement().appendChild(script);

        shareButton.setEnabled(true);

        /* TODO
        // update button
        lineChartButton.setFontAwesomeIcon(IconType.CIRCLE_O);
        barChartButton.setFontAwesomeIcon(IconType.CIRCLE_O);
        columnChartButton.setFontAwesomeIcon(IconType.CIRCLE_O);
        pieChartButton.setFontAwesomeIcon(IconType.CIRCLE_O);

        switch (type.toLowerCase()) {
            case "line":
                lineChartButton.setFontAwesomeIcon(IconType.CIRCLE);
                break;
            case "bar":
                barChartButton.setFontAwesomeIcon(IconType.CIRCLE);
                break;
            case "column":
                columnChartButton.setFontAwesomeIcon(IconType.CIRCLE);
                break;
            case "pie":
                pieChartButton.setFontAwesomeIcon(IconType.CIRCLE);
                break;
        }
        */
    }

    @Override
    public void setInSlot(Object slot, IsWidget content) {
        if (slot == AbstractApplicationPresenter.TYPE_SetMainContent) {
            rightContainer.clear();
            rightContainer.add(content);
        } else if (slot == AbstractApplicationPresenter.TOP_SLOT) {
            topContainer.clear();
            topContainer.add(content);
        } else if (slot == AbstractApplicationPresenter.LEFT_SLOT) {
            leftContainer.clear();
            leftContainer.add(content);
        } else {
            super.setInSlot(slot, content);
        }
    }

    /* TODO
    @UiHandler("lineChartButton")
    void onLineChartClicked(ClickEvent event) {
        getUiHandlers().onViewChanged("Line");
    }

    @UiHandler("barChartButton")
    void onBarChartClicked(ClickEvent event) {
        getUiHandlers().onViewChanged("Bar");
    }

    @UiHandler("columnChartButton")
    void onColumnChartClicked(ClickEvent event) {
        getUiHandlers().onViewChanged("Column");
    }

    @UiHandler("pieChartButton")
    void onPieChartClicked(ClickEvent event) {
        getUiHandlers().onViewChanged("Pie");
    }
    */

    @UiHandler("shareButton")
    void onShareClicked(ClickEvent event) {
        getUiHandlers().onShareClicked();
    }

    private void clear() {
        rightContainer.clear();
        rightContainer.getElement().removeAllChildren();
        rightContainer.setStyleName("");
    }

}