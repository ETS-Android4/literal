let styles = [%raw "require('./TextInput_Annotation.module.css')"];

[@react.component]
let make =
    (
      ~onTextChange,
      ~onTagsChange,
      ~textValue,
      ~tagsValue,
      ~disabled=?,
      ~autoFocus=?,
      ~placeholder=?,
      ~tagsInputRef=?,
      ~textInputRef=?,
    ) => {
  /** Reuse the ref prop if one was passed in, otherwise use our own **/
  let textInputRef = {
    let ownRef = React.useRef(Js.Nullable.null);
    switch (textInputRef) {
    | Some(textInputRef) => textInputRef
    | None => ownRef
    };
  };
  let tagsInputRef = {
    let ownRef = React.useRef(Js.Nullable.null);
    switch (tagsInputRef) {
    | Some(tagsInputRef) => tagsInputRef
    | None => ownRef
    };
  };

  let handleTextKeyDown = ev => {
    let handled =
      switch (ev->ReactEvent.Keyboard.keyCode) {
      | 51 /*** # **/ =>
        switch (tagsInputRef.current->Js.Nullable.toOption) {
        | Some(inputElem) =>
          let _ =
            inputElem
            ->Webapi.Dom.Element.unsafeAsHtmlElement
            ->Webapi.Dom.HtmlElement.focus;
          true;
        | None => false
        }
      | _ => false
      };
    let _ =
      if (handled) {
        let _ = ev->ReactEvent.Keyboard.preventDefault;
        let _ = ev->ReactEvent.Keyboard.stopPropagation;
        ();
      };
    ();
  };

  <>
    <TextInput_Basic
      ref=textInputRef
      onChange=onTextChange
      value=textValue
      ?placeholder
      ?autoFocus
      ?disabled
      inputProps={"onKeyDown": handleTextKeyDown, "disableUnderline": false}
    />
    <TagsList value=tagsValue onChange=onTagsChange />
  </>;
};