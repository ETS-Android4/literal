package io.literal.ui.fragment;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import io.literal.R;
import io.literal.factory.AppSyncClientFactory;
import io.literal.lib.Callback;
import io.literal.lib.JsonArrayUtil;
import io.literal.lib.WebEvent;
import io.literal.model.Annotation;
import io.literal.model.Body;
import io.literal.model.ExternalTarget;
import io.literal.model.HTMLScriptElement;
import io.literal.model.SourceWebViewAnnotation;
import io.literal.model.SpecificTarget;
import io.literal.model.Target;
import io.literal.model.TextualBody;
import io.literal.model.TextualTarget;
import io.literal.model.User;
import io.literal.repository.AnalyticsRepository;
import io.literal.repository.AnnotationRepository;
import io.literal.repository.BitmapRepository;
import io.literal.repository.ErrorRepository;
import io.literal.repository.ScriptRepository;
import io.literal.repository.ToastRepository;
import io.literal.repository.WebArchiveRepository;
import io.literal.repository.WebViewRepository;
import io.literal.service.CreateAnnotationIntent;
import io.literal.ui.view.SourceWebView.Client;
import io.literal.ui.view.SourceWebView.Source;
import io.literal.viewmodel.AppWebViewViewModel;
import io.literal.viewmodel.AuthenticationViewModel;
import io.literal.viewmodel.SourceWebViewViewModel;
import kotlin.jvm.functions.Function2;
import type.DeleteAnnotationInput;
import type.PatchAnnotationInput;
import type.PatchAnnotationOperationInput;

public class SourceWebView extends Fragment {

    private static final String PARAM_INITIAL_URL = "PARAM_INITIAL_URL";
    private static final String PARAM_BOTTOM_SHEET_APP_WEB_VIEW_VIEW_MODEL_KEY = "PARAM_BOTTOM_SHEET_APP_WEB_VIEW_VIEW_MODEL_KEY";
    private static final String PARAM_TOOLBAR_PRIMARY_ACTION_ICON_RESOURCE_ID = "PARAM_PRIMARY_TOOLBAR_ACTION_ICON_RESOURCE_ID";
    private static final String PARAM_PRIMARY_APP_WEB_VIEW_VIEW_MODEL_KEY = "PARAM_PRIMARY_APP_WEB_VIEW_VIEW_MODEL_KEY";

    private String paramInitialUrl;
    private String paramPrimaryAppWebViewViewModelKey;
    private String paramBottomSheetAppWebViewViewModelKey;
    private int paramToolbarPrimaryActionResourceId;

    private io.literal.ui.view.SourceWebView.SourceWebView webView;
    private Toolbar toolbar;
    private AppBarLayout appBarLayout;

    /**
     * Triggered when the primary action in the toolbar tapped.
     **/
    private Function2<Annotation[], Source, Void> onToolbarPrimaryActionCallback;
    /**
     * Triggered when ShareTargetHandler should be opened to URL
     **/
    private Callback<Void, URL> onCreateAnnotationFromSource;

    private ActionMode editAnnotationActionMode;
    /**
     * Show a different CAB if text is selected while editing annotation
     **/
    private boolean isEditingAnnotation;

    private SourceWebViewViewModel sourceWebViewViewModel;
    private AuthenticationViewModel authenticationViewModel;
    private AppWebViewViewModel bottomSheetAppWebViewViewModel;
    private AppWebViewViewModel primaryAppWebViewViewModel;

    public static SourceWebView newInstance(@NotNull String initialUrl, String bottomSheetAppWebViewViewModelKey, String primaryAppWebViewViewModelKey, @NotNull int toolbarPrimaryActionResourceId) {
        SourceWebView fragment = new SourceWebView();
        Bundle args = new Bundle();
        args.putString(PARAM_INITIAL_URL, initialUrl);
        args.putString(PARAM_BOTTOM_SHEET_APP_WEB_VIEW_VIEW_MODEL_KEY, bottomSheetAppWebViewViewModelKey);
        args.putString(PARAM_PRIMARY_APP_WEB_VIEW_VIEW_MODEL_KEY, primaryAppWebViewViewModelKey);
        args.putInt(PARAM_TOOLBAR_PRIMARY_ACTION_ICON_RESOURCE_ID, toolbarPrimaryActionResourceId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            paramInitialUrl = getArguments().getString(PARAM_INITIAL_URL);
            paramBottomSheetAppWebViewViewModelKey = getArguments().getString(PARAM_BOTTOM_SHEET_APP_WEB_VIEW_VIEW_MODEL_KEY);
            paramPrimaryAppWebViewViewModelKey = getArguments().getString(PARAM_PRIMARY_APP_WEB_VIEW_VIEW_MODEL_KEY);
            paramToolbarPrimaryActionResourceId = getArguments().getInt(PARAM_TOOLBAR_PRIMARY_ACTION_ICON_RESOURCE_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_source_web_view, container, false);
        setHasOptionsMenu(true);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sourceWebViewViewModel = new ViewModelProvider(requireActivity()).get(SourceWebViewViewModel.class);
        authenticationViewModel = new ViewModelProvider(requireActivity()).get(AuthenticationViewModel.class);
        bottomSheetAppWebViewViewModel =
                paramBottomSheetAppWebViewViewModelKey != null
                        ? new ViewModelProvider(requireActivity()).get(paramBottomSheetAppWebViewViewModelKey, AppWebViewViewModel.class)
                        : new ViewModelProvider(requireActivity()).get(AppWebViewViewModel.class);
        if (paramPrimaryAppWebViewViewModelKey != null) {
            primaryAppWebViewViewModel = new ViewModelProvider(requireActivity()).get(paramPrimaryAppWebViewViewModelKey, AppWebViewViewModel.class);
        }

        toolbar = view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

        appBarLayout = view.findViewById(R.id.app_bar_layout);

        webView = view.findViewById(R.id.source_web_view);
        webView.setOnAnnotationCreated(this::handleAnnotationCreated);
        webView.setOnAnnotationCancelEdit(this::handleAnnotationCancelEdit);
        webView.setOnAnnotationCommitEdit(this::handleAnnotationCommitEdit);
        webView.setOnGetTextSelectionMenu((e, data) -> isEditingAnnotation
                ? R.menu.source_webview_commit_edit_annotation_menu
                : R.menu.source_webview_create_annotation_menu);
        webView.setClientBuilder(new Client.Builder());
        webView.getClientBuilder()
                .ifPresent((clientBuilder) -> clientBuilder
                        .setContext(getContext())
                        .setUser(authenticationViewModel.getUser().getValue())
                        .setOnInjectAnnotationRendererScript((webview) -> Optional.ofNullable(getActivity())
                                .map((activity) -> {
                                    String script = ScriptRepository.getAnnotationRendererScript(
                                            getActivity().getAssets(),
                                            Arrays.stream(sourceWebViewViewModel.getAnnotations().getValue()).map(SourceWebViewAnnotation::getAnnotation).toArray(Annotation[]::new),
                                            sourceWebViewViewModel.getFocusedAnnotation().map((sourceWebViewAnnotation -> sourceWebViewAnnotation.getAnnotation().getId())).orElse(null)
                                    );
                                    return WebViewRepository.evaluateJavascript(webview, script).thenApply(_result -> ((Void) null));
                                })
                                .orElseGet(() -> {
                                   CompletableFuture<Void> future = new CompletableFuture<>();
                                   future.completeExceptionally(new Exception("Activity is null, unable to get AssetManager."));
                                   return future;
                                })));
        webView.setOnSourceChanged((source) -> {
            sourceWebViewViewModel.setSourceHasFinishedInitializing(false);
            toolbar.setTitle(source.getDisplayURI().getHost());

            Optional<Activity> activity = Optional.ofNullable(getActivity());
            Optional<Bitmap> favicon = source.getFavicon();

            if (activity.isPresent() && favicon.isPresent()) {
                toolbar.setLogo(
                        new BitmapDrawable(
                                activity.get().getResources(),
                                BitmapRepository.scaleAndAddBackground(getContext(), favicon.get())
                        )
                );
            }
            return null;
        });
        webView.setOnReceivedIcon((icon) -> {
            Optional<Activity> activity = Optional.ofNullable(getActivity());
            if (activity.isPresent() && Optional.ofNullable(icon).isPresent()) {
                toolbar.setLogo(
                        new BitmapDrawable(
                                activity.get().getResources(),
                                BitmapRepository.scaleAndAddBackground(getContext(), icon)
                        )
                );
            }
            return null;
        });

        sourceWebViewViewModel.getAnnotations().observe(getActivity(), (annotations) -> {
            if (this.webView == null) {
                ErrorRepository.captureException(new Exception("Expected webView, but found none."));
                return;
            }

            this.handleRenderAnnotations(annotations);
        });

        webView.setWebEventCallback((webView, event) -> {
            switch (event.getType()) {
                case WebEvent.TYPE_FOCUS_ANNOTATION:
                    try {
                        String annotationId = event.getData().getString("annotationId");
                        JSONObject boundingBox = event.getData().getJSONObject("boundingBox");
                        Rect annotationBoundingBox = new Rect(
                                boundingBox.getInt("left"),
                                boundingBox.getInt("top"),
                                boundingBox.getInt("right"),
                                boundingBox.getInt("bottom")
                        );

                        handleAnnotationFocus(annotationId, annotationBoundingBox);
                    } catch (JSONException ex) {
                        ErrorRepository.captureException(ex, event.getData().toString());
                    }
                    break;
                case WebEvent.TYPE_BLUR_ANNOTATION:
                    if (sourceWebViewViewModel.getFocusedAnnotation().isPresent()) {
                        handleAnnotationBlur();
                    }
                    break;
                case WebEvent.TYPE_SELECTION_CREATED:
                    try {
                        JSONObject rawBoundingBox = event.getData().getJSONObject("boundingBox");
                        Rect boundingBox = new Rect(
                                rawBoundingBox.getInt("left"),
                                rawBoundingBox.getInt("top"),
                                rawBoundingBox.getInt("right"),
                                rawBoundingBox.getInt("bottom")
                        );
                        webView.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, (float) boundingBox.left, (float) boundingBox.top, 0));
                        webView.dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, (float) boundingBox.left, (float) boundingBox.top, 0));
                    } catch (JSONException ex) {
                        ErrorRepository.captureException(ex, event.getData().toString());
                    }
                    break;
                case WebEvent.TYPE_ANNOTATION_RENDERER_INITIALIZED:
                    sourceWebViewViewModel.setSourceHasFinishedInitializing(true);
                    break;
                case WebEvent.TYPE_SELECTION_CHANGE:
                    this.handleSelectionChange(event.getData().optBoolean("isCollapsed", true));
                    break;
            }
            return null;
        });

        bottomSheetAppWebViewViewModel.getReceivedWebEvents().observe(requireActivity(), (webEvents) -> {
            if (webEvents == null) {
                return;
            }

            webEvents.iterator().forEachRemaining((webEvent) -> {
                switch (webEvent.getType()) {
                    case WebEvent.TYPE_SET_VIEW_STATE:
                        try {
                            String state = webEvent.getData().getString("state");
                            this.handleSetBottomSheetState(state);
                        } catch (JSONException e) {
                            ErrorRepository.captureException(e);
                        }
                        return;
                    case WebEvent.TYPE_EDIT_ANNOTATION_TAGS_RESULT:
                        try {
                            Annotation newAnnotation = Annotation.fromJson(webEvent.getData());
                            this.handleAnnotationTextualBodyChange(newAnnotation);
                        } catch (JSONException e) {
                            ErrorRepository.captureException(e);
                        }
                        return;
                }
            });

            bottomSheetAppWebViewViewModel.clearReceivedWebEvents();
        });

        bottomSheetAppWebViewViewModel.getBottomSheetState().observe(requireActivity(), (bottomSheetState) -> {
            switch (bottomSheetState) {
                case BottomSheetBehavior.STATE_COLLAPSED:
                    appBarLayout.setExpanded(true);
                    break;
                case BottomSheetBehavior.STATE_EXPANDED:
                    appBarLayout.setExpanded(false);
                    break;
            }
        });

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        }

        if (paramInitialUrl != null) {
            try {
                webView.setSource(new Source(new URI(paramInitialUrl), Optional.empty()), false);
            } catch (Exception e) {
                ErrorRepository.captureException(e);
            }
        }
    }

    private void handleSetBottomSheetState(String state) {
        if (editAnnotationActionMode != null) {
            webView.finishEditAnnotationActionMode(editAnnotationActionMode);
            editAnnotationActionMode = null;
            isEditingAnnotation = false;
        }
        switch (state) {
            case "COLLAPSED_ANNOTATION_TAGS":
                bottomSheetAppWebViewViewModel.setBottomSheetState(
                        sourceWebViewViewModel.getFocusedAnnotation().isPresent() ? BottomSheetBehavior.STATE_COLLAPSED : BottomSheetBehavior.STATE_HIDDEN
                );
                break;
            case "EDIT_ANNOTATION_TAGS":
                bottomSheetAppWebViewViewModel.setBottomSheetState(
                        sourceWebViewViewModel.getFocusedAnnotation().isPresent() ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_HIDDEN
                );
                break;
        }
    }

    private Void handleAnnotationCreated(ActionMode mode) {

        /**
         * If we're trying to create a new annotation from an archived source, open the original
         * source instead and prompt the annotation to be created there. There's definitely a better
         * UX for this.
         */
        if (webView.getSource().map(s -> s.getType().equals(Source.Type.WEB_ARCHIVE)).orElse(false) &&
                this.onCreateAnnotationFromSource != null) {
            try {
                this.onCreateAnnotationFromSource.invoke(null, webView.getSource().get().getDisplayURI().toURL());
                return null;
            } catch (MalformedURLException e) {
                ErrorRepository.captureException(e);
            }
        }

        CompletableFuture<String> annotationsFuture = WebViewRepository.evaluateJavascript(
                webView,
                ScriptRepository.getGetAnnotationScript(getActivity().getAssets())
        );
        CompletableFuture<ArrayList<HTMLScriptElement>> scriptsFuture = WebViewRepository.getPageScriptElements(
                webView,
                ScriptRepository.getGetScriptsScript(getActivity().getAssets())
        );
        CompletableFuture.allOf(annotationsFuture, scriptsFuture)
                .thenCompose((_void) -> {
                    mode.finish();
                    return WebArchiveRepository.capture(
                            getContext(),
                            webView,
                            scriptsFuture.getNow(new ArrayList<>())
                    );
                })
                .whenComplete((webArchive, error) -> {
                    if (error != null) {
                        ErrorRepository.captureException(error);
                        Optional.ofNullable(getActivity()).ifPresent((activity) -> {
                            ToastRepository.show(activity, R.string.toast_error_annotation_created);

                        });
                        return;
                    }

                    try {
                        String annotationsJSON = annotationsFuture.get();
                        String appSyncIdentity = authenticationViewModel.getUser().getValue().getAppSyncIdentity();

                        Optional.ofNullable(getActivity()).ifPresent((activity) -> activity.runOnUiThread(() -> {
                            Annotation annotation = sourceWebViewViewModel.createAnnotation(annotationsJSON, appSyncIdentity, webArchive);
                            sourceWebViewViewModel.setFocusedAnnotationId(annotation.getId());
                            bottomSheetAppWebViewViewModel.setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED);
                        }));
                    } catch (Exception e) {
                        ErrorRepository.captureException(e);
                        Optional.ofNullable(getActivity()).ifPresent(activity -> ToastRepository.show(activity, R.string.toast_error_annotation_created));
                    }
                });

        return null;
    }

    private void handleAnnotationFocus(String annotationId, Rect annotationBoundingBox) {
        Optional<SourceWebViewAnnotation> optionalAnnotation = sourceWebViewViewModel.getAnnotation(annotationId);
        if (!optionalAnnotation.isPresent()) {
            ErrorRepository.captureException(new Exception("handleAnnotationClicked unable to find annotationId"));
            return;
        }

        SourceWebViewAnnotation annotation = optionalAnnotation.get();
        if (!annotation.getFocusStatus().equals(SourceWebViewAnnotation.FocusStatus.FOCUSED)) {
            sourceWebViewViewModel.setFocusedAnnotationId(annotation.getAnnotation().getId());
        }

        bottomSheetAppWebViewViewModel.setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);

        if (editAnnotationActionMode != null) {
            webView.finishEditAnnotationActionMode(editAnnotationActionMode);
            editAnnotationActionMode = null;
        }

        try {
            String getAnnotationBoundingBoxScript = ScriptRepository.getGetAnnotationBoundingBoxScript(
                    getActivity().getAssets(),
                    annotation.getAnnotation().toJson()
            );
            editAnnotationActionMode = webView.startEditAnnotationActionMode(
                    getAnnotationBoundingBoxScript,
                    annotationBoundingBox,
                    (_e, _d) -> handleAnnotationEdit(annotationId),
                    (_e, _d) -> handleAnnotationDelete(annotationId)
            );
        } catch (JSONException e) {
            ErrorRepository.captureException(e);
        }
    }

    private Void handleAnnotationCancelEdit(ActionMode mode) {
        if (mode != null) {
            mode.finish();
        }

        Optional<SourceWebViewAnnotation> focusedAnnotation = sourceWebViewViewModel.getFocusedAnnotation();
        if (focusedAnnotation.isPresent()) {
            try {
                JSONObject blurAnnotationData = new JSONObject();
                blurAnnotationData.put("annotationId", focusedAnnotation.get().getAnnotation().getId());
                webView.postWebEvent(new WebEvent(
                        WebEvent.TYPE_BLUR_ANNOTATION,
                        UUID.randomUUID().toString(),
                        blurAnnotationData
                ));
            } catch (JSONException ex) {
                ErrorRepository.captureException(ex);
            }
            sourceWebViewViewModel.setFocusedAnnotationId(null);
            bottomSheetAppWebViewViewModel.setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        } else {
            // If a focused annotation had been blurred, a rerender would've already occurred, but
            // here we must manually rerender annotations to restore the unedited version.
            this.handleRenderAnnotations(sourceWebViewViewModel.getAnnotations().getValue());
        }

        if (editAnnotationActionMode != null) {
            webView.finishEditAnnotationActionMode(editAnnotationActionMode);
            editAnnotationActionMode = null;
        }
        isEditingAnnotation = false;

        return null;
    }

    private Void handleAnnotationCommitEdit(ActionMode mode) {
        String script = ScriptRepository.getGetAnnotationScript(getActivity().getAssets());
        Optional<SourceWebViewAnnotation> optionalAnnotation = sourceWebViewViewModel.getFocusedAnnotation();
        if (!optionalAnnotation.isPresent()) {
            ErrorRepository.captureException(new Exception("Expected focusedAnnotation, but found null."));
            return null;
        }

        SourceWebViewAnnotation sourceWebViewAnnotation = optionalAnnotation.get();
        Annotation annotation = sourceWebViewAnnotation.getAnnotation();
        webView.evaluateJavascript(script, value -> {
            mode.finish();
            try {
                Annotation parsedAnnotation = Annotation.fromJson(new JSONObject(value));

                ArrayList<Target> updatedTarget = new ArrayList<>();
                ArrayList<PatchAnnotationOperationInput> patchAnnotationOperationInputs = new ArrayList<>();
                Arrays.stream(parsedAnnotation.getTarget()).forEach((target) -> {
                    Optional<Target> existingTarget = Arrays
                            .stream(annotation.getTarget())
                            .filter((t) -> t.getType().equals(target.getType()))
                            .findFirst();
                    if (!existingTarget.isPresent()) {
                        updatedTarget.add(target);
                        patchAnnotationOperationInputs.add(target.toPatchAnnotationOperationInputAdd());
                    }

                    if (target.getType() == Target.Type.SPECIFIC_TARGET) {
                        SpecificTarget existingSpecificTarget = (SpecificTarget) target;
                        SpecificTarget updatedSpecificTarget = new SpecificTarget(
                                ((SpecificTarget) existingTarget.get()).getId(),
                                existingSpecificTarget.getSource(),
                                existingSpecificTarget.getSelector(),
                                existingSpecificTarget.getState()
                        );
                        updatedTarget.add(updatedSpecificTarget);
                        patchAnnotationOperationInputs.add(updatedSpecificTarget.toPatchAnnotationOperationInputSet());
                    } else if (target.getType() == Target.Type.EXTERNAL_TARGET) {
                        ExternalTarget existingExternalTarget = (ExternalTarget) target;
                        ExternalTarget updatedExternalTarget = new ExternalTarget(
                                ((ExternalTarget) existingTarget.get()).getId(),
                                existingExternalTarget.getFormat(),
                                existingExternalTarget.getLanguage(),
                                existingExternalTarget.getProcessingLanguage(),
                                existingExternalTarget.getTextDirection(),
                                existingExternalTarget.getAccessibility(),
                                existingExternalTarget.getRights(),
                                existingExternalTarget.getResourceType()
                        );
                        updatedTarget.add(updatedExternalTarget);
                        patchAnnotationOperationInputs.add(updatedExternalTarget.toPatchAnnotationOperationInputSet());
                    } else if (target.getType() == Target.Type.TEXTUAL_TARGET) {
                        TextualTarget existingTextualTarget = (TextualTarget) target;
                        TextualTarget updatedTextualTarget = new TextualTarget(
                                ((TextualTarget) existingTarget.get()).getId(),
                                existingTextualTarget.getFormat(),
                                existingTextualTarget.getLanguage(),
                                existingTextualTarget.getProcessingLanguage(),
                                existingTextualTarget.getTextDirection(),
                                existingTextualTarget.getAccessibility(),
                                existingTextualTarget.getRights(),
                                existingTextualTarget.getValue()
                        );
                        updatedTarget.add(updatedTextualTarget);
                        patchAnnotationOperationInputs.add(updatedTextualTarget.toPatchAnnotationOperationInputSet());
                    }
                });
                Annotation updatedAnnotation = new Annotation(
                        annotation.getBody(),
                        updatedTarget.toArray(new Target[0]),
                        annotation.getMotivation(),
                        annotation.getCreated(),
                        annotation.getModified(),
                        annotation.getId()
                );
                User user = authenticationViewModel.getUser().getValue();

                if (sourceWebViewAnnotation.getCreationStatus().equals(SourceWebViewAnnotation.CreationStatus.CREATED)) {
                    PatchAnnotationInput input = PatchAnnotationInput.builder()
                            .creatorUsername(user.getAppSyncIdentity())
                            .id(updatedAnnotation.getId())
                            .operations(patchAnnotationOperationInputs).build();
                    AnnotationRepository.patchAnnotationMutation(
                            AppSyncClientFactory.getInstanceForUser(getContext(), user),
                            input,
                            (e, data) -> {
                                if (e != null) {
                                    ErrorRepository.captureException(e);
                                }
                            }
                    );
                }
                boolean updated = sourceWebViewViewModel.updateAnnotation(updatedAnnotation);
                if (!updated) {
                    ErrorRepository.captureException(new Exception("Failed to update viewmodel for annotation"));
                }

                if (primaryAppWebViewViewModel != null) {
                    try {
                        JSONObject setCacheAnnotationData = new JSONObject();
                        setCacheAnnotationData.put("annotation", updatedAnnotation.toJson());
                        getActivity().runOnUiThread(() -> primaryAppWebViewViewModel.dispatchWebEvent(
                                new WebEvent(
                                        WebEvent.TYPE_SET_CACHE_ANNOTATION,
                                        UUID.randomUUID().toString(),
                                        setCacheAnnotationData
                                )
                        ));
                    } catch (JSONException ex) {
                        ErrorRepository.captureException(ex);
                    }
                }
            } catch (JSONException e) {
                ErrorRepository.captureException(e);
            } finally {
                if (editAnnotationActionMode != null) {
                    webView.finishEditAnnotationActionMode(editAnnotationActionMode);
                    editAnnotationActionMode = null;
                }
                isEditingAnnotation = false;

                Optional<SourceWebViewAnnotation> focusedAnnotation = sourceWebViewViewModel.getFocusedAnnotation();
                if (!focusedAnnotation.isPresent()) {
                    try {
                        JSONObject blurAnnotationData = new JSONObject();
                        blurAnnotationData.put("annotationId", focusedAnnotation.get().getAnnotation().getId());
                        webView.postWebEvent(new WebEvent(
                                WebEvent.TYPE_BLUR_ANNOTATION,
                                UUID.randomUUID().toString(),
                                blurAnnotationData
                        ));
                    } catch (JSONException ex) {
                        ErrorRepository.captureException(ex);
                    }
                    sourceWebViewViewModel.setFocusedAnnotationId(null);
                    bottomSheetAppWebViewViewModel.setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }
        });

        return null;
    }

    private void handleAnnotationTextualBodyChange(Annotation newAnnotation) {
        Optional<SourceWebViewAnnotation> optionalSourceWebViewAnnotation = sourceWebViewViewModel.getAnnotation(newAnnotation.getId());
        boolean shouldPatchAnnotation = optionalSourceWebViewAnnotation
                .map(sourceWebViewAnnotation -> sourceWebViewAnnotation.getCreationStatus().equals(SourceWebViewAnnotation.CreationStatus.CREATED))
                .orElse(false);
        if (shouldPatchAnnotation) {
            Annotation annotation = optionalSourceWebViewAnnotation.get().getAnnotation();
            List<TextualBody> body = Arrays.stream(Optional.ofNullable(annotation.getBody()).orElse(new Body[0]))
                    .filter(b -> b.getType().equals(Body.Type.TEXTUAL_BODY))
                    .map(b -> ((TextualBody) b))
                    .collect(Collectors.toList());
            List<TextualBody> newBody = Arrays.stream(Optional.ofNullable(newAnnotation.getBody()).orElse(new Body[0]))
                    .filter(b -> b.getType().equals(Body.Type.TEXTUAL_BODY))
                    .map(b -> ((TextualBody) b))
                    .collect(Collectors.toList());

            HashSet<String> bodyIds = body.stream().map(TextualBody::getId).collect(Collectors.toCollection(HashSet::new));
            HashSet<String> newBodyIds = newBody.stream().map(TextualBody::getId).collect(Collectors.toCollection(HashSet::new));

            HashSet<String> removedBodyIds = (HashSet<String>) bodyIds.clone();
            removedBodyIds.removeAll(newBodyIds);
            HashSet<String> addedBodyIds = (HashSet<String>) newBodyIds.clone();
            addedBodyIds.removeAll(bodyIds);

            ArrayList<PatchAnnotationOperationInput> operations = new ArrayList<>();
            operations.addAll(
                    removedBodyIds.stream()
                            .map(id -> body.stream().filter(b -> b.getId().equals(id)).findFirst().get().toPatchAnnotationOperationInputRemove())
                            .collect(Collectors.toList())
            );
            operations.addAll(
                    addedBodyIds.stream()
                            .map(id -> newBody.stream().filter(b -> b.getId().equals(id)).findFirst().get().toPatchAnnotationOperationInputAdd())
                            .collect(Collectors.toList())
            );

            User user = authenticationViewModel.getUser().getValue();
            AnnotationRepository.patchAnnotationMutation(
                    AppSyncClientFactory.getInstanceForUser(getContext(), user),
                    PatchAnnotationInput.builder()
                            .creatorUsername(user.getAppSyncIdentity())
                            .id(newAnnotation.getId())
                            .operations(operations)
                            .build(),
                    (e, data) -> {
                        if (e != null) {
                            ErrorRepository.captureException(e);
                            return;
                        }
                    }
            );

            if (primaryAppWebViewViewModel != null) {
                try {
                    JSONObject setCacheAnnotationData = new JSONObject();
                    setCacheAnnotationData.put("annotation", newAnnotation.toJson());
                    getActivity().runOnUiThread(() -> primaryAppWebViewViewModel.dispatchWebEvent(
                            new WebEvent(
                                    WebEvent.TYPE_SET_CACHE_ANNOTATION,
                                    UUID.randomUUID().toString(),
                                    setCacheAnnotationData
                            )
                    ));
                } catch (JSONException ex) {
                    ErrorRepository.captureException(ex);
                }
            }
        }

        boolean updated = sourceWebViewViewModel.updateAnnotation(newAnnotation);
        if (!updated) {
            ErrorRepository.captureException(new Exception("Failed to update viewmodel for annotation"));
        }

    }

    private void handleSelectionChange(boolean isCollapsed) {
        if (isEditingAnnotation && isCollapsed) {
            this.handleAnnotationCancelEdit(null);
        }
    }

    public Optional<Source> handleViewTargetForAnnotation(Annotation annotation, String targetId) {
        Optional<Source> optionalSource = Source.createFromAnnotation(getContext(), annotation, targetId);
        optionalSource.ifPresent((source) -> {
            boolean shouldLoadSource = webView.getSource().map(currentSource -> {
                if (!currentSource.getType().equals(source.getType())) {
                    return true;
                }

                if (currentSource.getType().equals(Source.Type.WEB_ARCHIVE)) {
                    Optional<String> currentStorageObjectId = currentSource.getWebArchive().map(w -> w.getStorageObject().getId());
                    Optional<String> storageObjectId = source.getWebArchive().map(w -> w.getStorageObject().getId());
                    return !currentStorageObjectId.isPresent() || !storageObjectId.isPresent() || !storageObjectId.equals(currentStorageObjectId);
                }

                if (currentSource.getType().equals(Source.Type.EXTERNAL_SOURCE)) {
                    Optional<URI> currentURI = currentSource.getURI();
                    Optional<URI> nextURI = source.getURI();
                    return !currentURI.isPresent() || !nextURI.isPresent() || !currentURI.equals(nextURI);
                }

                return true;
            }).orElse(true);

            if (shouldLoadSource) {
                sourceWebViewViewModel.reset();
                webView.setSource(source, true);
            }

            SourceWebViewAnnotation[] annotations = sourceWebViewViewModel.getAnnotations().getValue();
            boolean shouldAddAnnotation =
                    Arrays.stream(annotations)
                            .noneMatch(committedAnnotation -> committedAnnotation.getAnnotation().getId().equals(annotation.getId()));
            if (shouldAddAnnotation) {
                sourceWebViewViewModel.addAnnotation(annotation, true);
            } else {
                sourceWebViewViewModel.setFocusedAnnotationId(annotation.getId());
            }
        });

        return optionalSource;
    }

    private void handleRenderAnnotations(SourceWebViewAnnotation[] annotations) {
        if (!Optional.ofNullable(sourceWebViewViewModel.getSourceHasFinishedInitializing()).map(LiveData::getValue).orElse(false)) {
            ErrorRepository.captureWarning(new Exception("handleRenderAnnotations: expected webview to be initialized, nooping."));
            return;
        }

        try {
            JSONObject renderAnnotationsData = new JSONObject();
            renderAnnotationsData.put(
                    "annotations",
                    JsonArrayUtil.stringifyObjectArray(
                            Arrays.stream(annotations).map(SourceWebViewAnnotation::getAnnotation).toArray(Annotation[]::new),
                            Annotation::toJson
                    )
            );
            Optional<String> focusedAnnotationId = Arrays.stream(annotations)
                    .filter((sourceWebViewAnnotation -> sourceWebViewAnnotation.getFocusStatus().equals(SourceWebViewAnnotation.FocusStatus.FOCUSED)))
                    .findFirst()
                    .map((sourceWebViewAnnotation -> sourceWebViewAnnotation.getAnnotation().getId()));
            if (focusedAnnotationId.isPresent()) {
                renderAnnotationsData.put("focusedAnnotationId", focusedAnnotationId.get());
            }
            webView.postWebEvent(new WebEvent(
                    WebEvent.TYPE_RENDER_ANNOTATIONS,
                    UUID.randomUUID().toString(),
                    renderAnnotationsData
            ));
        } catch (JSONException ex) {
            ErrorRepository.captureException(ex);
        }
    }

    public void handleAnnotationBlur() {
        if (bottomSheetAppWebViewViewModel.getBottomSheetState().getValue() == BottomSheetBehavior.STATE_EXPANDED) {
            return;
        }

        bottomSheetAppWebViewViewModel.setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);
        sourceWebViewViewModel.setFocusedAnnotationId(null);
        if (editAnnotationActionMode != null) {
            webView.finishEditAnnotationActionMode(editAnnotationActionMode);
            editAnnotationActionMode = null;
            isEditingAnnotation = false;
        }
    }

    private void handleAnnotationEdit(String annotationId) {
        if (this.editAnnotationActionMode != null) {
            webView.finishEditAnnotationActionMode(editAnnotationActionMode);
            editAnnotationActionMode = null;
            isEditingAnnotation = false;
        }
        isEditingAnnotation = true;
        try {
            JSONObject editAnnotationData = new JSONObject();
            editAnnotationData.put("annotationId", annotationId);
            webView.postWebEvent(new WebEvent(
                    WebEvent.TYPE_EDIT_ANNOTATION,
                    UUID.randomUUID().toString(),
                    editAnnotationData
            ));
        } catch (JSONException ex) {
            ErrorRepository.captureException(ex);
        }
    }

    private void handleAnnotationDelete(String annotationId) {
        Optional<SourceWebViewAnnotation> optionalAnnotation = sourceWebViewViewModel.getFocusedAnnotation();
        if (!optionalAnnotation.isPresent()) {
            ErrorRepository.captureException(new Exception("handleAnnotationDelete expected focusedAnnotation, but found null."));
            return;
        }
        SourceWebViewAnnotation annotation = optionalAnnotation.get();
        if (!annotation.getAnnotation().getId().equals(annotationId)) {
            ErrorRepository.captureException(new Exception("handleAnnotationDelete expected focusedAnnotation.id to equal annotationId, but found mismatch."));
            return;
        }

        sourceWebViewViewModel.setFocusedAnnotationId(null);
        bottomSheetAppWebViewViewModel.setBottomSheetState(BottomSheetBehavior.STATE_HIDDEN);

        if (this.editAnnotationActionMode != null) {
            webView.finishEditAnnotationActionMode(editAnnotationActionMode);
            editAnnotationActionMode = null;
            isEditingAnnotation = false;
        }

        boolean updated = sourceWebViewViewModel.removeAnnotation(annotationId);
        if (!updated) {
            ErrorRepository.captureException(new Exception("handleAnnotationDelete: Unable to find annotation for id " + annotationId));
            return;
        }

        if (annotation.getCreationStatus().equals(SourceWebViewAnnotation.CreationStatus.CREATED)) {
            User user = authenticationViewModel.getUser().getValue();
            AnnotationRepository.deleteAnnotationMutation(
                    AppSyncClientFactory.getInstanceForUser(getContext(), user),
                    DeleteAnnotationInput.builder()
                            .creatorUsername(user.getAppSyncIdentity())
                            .id(annotationId)
                            .build(),
                    (e, _data) -> {
                        if (e != null) {
                            ErrorRepository.captureException(e);
                            return;
                        }
                        try {
                            JSONObject deleteCacheAnnotationData = new JSONObject();
                            deleteCacheAnnotationData.put("annotation", annotation.getAnnotation().toJson());
                            getActivity().runOnUiThread(() -> bottomSheetAppWebViewViewModel.dispatchWebEvent(
                                    new WebEvent(
                                            WebEvent.TYPE_DELETE_CACHE_ANNOTATION,
                                            UUID.randomUUID().toString(),
                                            deleteCacheAnnotationData
                                    )
                            ));
                        } catch (JSONException ex) {
                            ErrorRepository.captureException(ex);
                        }
                    }
            );
        }
    }

    public void setOnToolbarPrimaryActionCallback(Function2<Annotation[], Source, Void> onToolbarPrimaryActionCallback) {
        this.onToolbarPrimaryActionCallback = onToolbarPrimaryActionCallback;
    }

    private CompletableFuture<Void> handleToolbarPrimaryAction() {
        try {
            JSONObject data = new JSONObject();
            String primaryActionResource;
            if (paramToolbarPrimaryActionResourceId == R.drawable.done_white) {
                primaryActionResource = "done_white";
            } else if (paramToolbarPrimaryActionResourceId == R.drawable.arrow_drop_down_white) {
                primaryActionResource = "arrow_drop_down_white";
            } else {
                primaryActionResource = (Integer.valueOf(paramToolbarPrimaryActionResourceId)).toString();
            }
            data.put("action", "toolbar primary action");
            data.put("toolbar primary action resource", primaryActionResource);
            AnalyticsRepository.logEvent(AnalyticsRepository.TYPE_CLICK, data);
        } catch (JSONException e) {
            ErrorRepository.captureException(e);
        }

        CompletableFuture<Void> creationFuture;
        SourceWebViewAnnotation[] annotationsToCreate =
            Arrays.stream(sourceWebViewViewModel.getAnnotations().getValue())
                    .filter(sourceWebViewAnnotation -> sourceWebViewAnnotation.getCreationStatus().equals(SourceWebViewAnnotation.CreationStatus.REQUIRES_CREATION))
                    .toArray(SourceWebViewAnnotation[]::new);
        if (annotationsToCreate.length > 0) {
            // update toolbar primary action to be loader

            creationFuture = CompletableFuture.allOf(
                Arrays.stream(annotationsToCreate)
                    .map((sourcewWebViewAnnotation) ->
                            sourcewWebViewAnnotation.compileWebArchive(
                                    getContext(),
                                    authenticationViewModel.getUser().getValue()
                            )
                    )
                    .toArray(CompletableFuture[]::new)
            ).whenComplete((_void, e) -> {
                if (e != null) {
                    return;
                }

                Log.i("handleToolbarPrimaryAction", "web archives compiled");

                String id = UUID.randomUUID().toString();
                Arrays.stream(annotationsToCreate).forEach(annotationToCreate -> {
                    CreateAnnotationIntent intent = (new CreateAnnotationIntent.Builder())
                            .setAnnotation(annotationToCreate.getAnnotation())
                            .setDisplayURI(webView.getSource().map(Source::getDisplayURI))
                            .setFavicon(webView.getSource().flatMap(Source::getFavicon).flatMap(f -> BitmapRepository.toFile(getContext(), f)))
                            .setId(id)
                            .build();

                    try {
                        JSONObject data = new JSONObject();
                        data.put("type", CreateAnnotationIntent.class.getName());
                        data.put("data", intent.toJSON());
                        AnalyticsRepository.logEvent(AnalyticsRepository.TYPE_DISPATCH_SERVICE_INTENT, data);
                    } catch (JSONException err) {
                        ErrorRepository.captureException(err);
                    }

                    intent.toIntent(getContext()).ifPresent((i) -> getActivity().startService(i));
                });
            });

        } else {
            creationFuture = CompletableFuture.completedFuture(null);
        }

        creationFuture.whenComplete((_void, e) -> {
            if (e != null) {
                ErrorRepository.captureException(e);
                return;
            }

            if (onToolbarPrimaryActionCallback != null && webView.getSource().isPresent()) {
                onToolbarPrimaryActionCallback.invoke(
                        Arrays.stream(annotationsToCreate).map(SourceWebViewAnnotation::getAnnotation).toArray(Annotation[]::new),
                        webView.getSource().get()
                );
            } else {
                Activity activity = getActivity();
                if (activity != null) {
                    activity.setResult(Activity.RESULT_CANCELED);
                    activity.finish();
                }
            }
        });

        return creationFuture;
    }

    public io.literal.ui.view.SourceWebView.SourceWebView getWebView() {
        return webView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this.webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.source_webview_toolbar, menu);

        menu.getItem(0).setIcon(paramToolbarPrimaryActionResourceId);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.primary:
                item.setActionView(R.layout.webview_toolbar_indeterminate_progress);
                this.handleToolbarPrimaryAction()
                        .whenComplete((_void, _err) -> item.setActionView(null));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setOnCreateAnnotationFromSource(Callback<Void, URL> onCreateAnnotationFromSource) {
        this.onCreateAnnotationFromSource = onCreateAnnotationFromSource;
    }
}