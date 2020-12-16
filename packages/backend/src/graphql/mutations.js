/* eslint-disable */
// this is an auto generated file. This will be overwritten

export const createAnnotation = /* GraphQL */ `
  mutation CreateAnnotation($input: CreateAnnotationInput!) {
    createAnnotation(input: $input) {
      annotation {
        context
        type
        id
        created
        generated
        modified
        motivation
        via
        canonical
        creatorUsername
      }
      annotationCollections {
        context
        id
        type
        label
        total
        firstAnnotationPageId
        lastAnnotationPageId
        creatorUsername
        created
        modified
      }
      annotationPageItems {
        id
        created
        modified
        annotationId
        annotationPageId
        creatorUsername
      }
      annotationPage {
        context
        id
        type
        startIndex
        created
        modified
        partOfAnnotationCollectionId
        nextAnnotationPageId
        prevAnnotationPageId
        creatorUsername
      }
    }
  }
`;
export const updateAnnotation = /* GraphQL */ `
  mutation UpdateAnnotation($input: UpdateAnnotationInput!) {
    updateAnnotation(input: $input) {
      annotation {
        context
        type
        id
        created
        generated
        modified
        motivation
        via
        canonical
        creatorUsername
      }
      createdAnnotationCollections {
        context
        id
        type
        label
        total
        firstAnnotationPageId
        lastAnnotationPageId
        creatorUsername
        created
        modified
      }
      createdAnnotationPageItems {
        id
        created
        modified
        annotationId
        annotationPageId
        creatorUsername
      }
      createdAnnotationPages {
        context
        id
        type
        startIndex
        created
        modified
        partOfAnnotationCollectionId
        nextAnnotationPageId
        prevAnnotationPageId
        creatorUsername
      }
      deletedAnnotationCollectionIds
      deletedAnnotationPageItemIds
      deletedAnnotationPageIds
    }
  }
`;
export const deleteAnnotation = /* GraphQL */ `
  mutation DeleteAnnotation($input: DeleteAnnotationInput!) {
    deleteAnnotation(input: $input) {
      annotation {
        context
        type
        id
        created
        generated
        modified
        motivation
        via
        canonical
        creatorUsername
      }
    }
  }
`;
export const patchAnnotation = /* GraphQL */ `
  mutation PatchAnnotation($input: PatchAnnotationInput!) {
    patchAnnotation(input: $input) {
      annotation {
        context
        type
        id
        created
        generated
        modified
        motivation
        via
        canonical
        creatorUsername
      }
      createdAnnotationCollections {
        context
        id
        type
        label
        total
        firstAnnotationPageId
        lastAnnotationPageId
        creatorUsername
        created
        modified
      }
      createdAnnotationPageItems {
        id
        created
        modified
        annotationId
        annotationPageId
        creatorUsername
      }
      createdAnnotationPages {
        context
        id
        type
        startIndex
        created
        modified
        partOfAnnotationCollectionId
        nextAnnotationPageId
        prevAnnotationPageId
        creatorUsername
      }
      deletedAnnotationCollectionIds
      deletedAnnotationPageItemIds
      deletedAnnotationPageIds
    }
  }
`;
export const createAnnotationFromExternalTarget = /* GraphQL */ `
  mutation CreateAnnotationFromExternalTarget(
    $input: CreateAnnotationFromExternalTargetInput!
  ) {
    createAnnotationFromExternalTarget(input: $input) {
      context
      type
      id
      body {
        ... on ExternalBody {
          id
          format
          language
          processingLanguage
          textDirection
          accessibility
          rights
          type
        }
        ... on TextualBody {
          id
          format
          language
          processingLanguage
          textDirection
          accessibility
          purpose
          rights
          value
          type
        }
        ... on ChoiceBody {
          type
        }
        ... on SpecificBody {
          id
          type
          purpose
          styleClass
          scope
        }
      }
      target {
        ... on ExternalTarget {
          id
          format
          language
          processingLanguage
          textDirection
          type
          accessibility
          rights
        }
        ... on TextualTarget {
          id
          format
          language
          processingLanguage
          textDirection
          accessibility
          rights
          value
        }
      }
      created
      generator {
        id
        type
        name
        nickname
        email_sha1
        email
        homepage
        username
        created
        modified
      }
      generated
      modified
      audience {
        id
        type
      }
      motivation
      via
      canonical
      stylesheet {
        type
        value
      }
      creator {
        id
        type
        name
        nickname
        email_sha1
        email
        homepage
        username
        created
        modified
      }
      creatorUsername
    }
  }
`;
export const createAgent = /* GraphQL */ `
  mutation CreateAgent(
    $input: CreateAgentInput!
    $condition: ModelAgentConditionInput
  ) {
    createAgent(input: $input, condition: $condition) {
      id
      type
      name
      nickname
      email_sha1
      email
      homepage
      username
      created
      modified
    }
  }
`;
export const updateAgent = /* GraphQL */ `
  mutation UpdateAgent(
    $input: UpdateAgentInput!
    $condition: ModelAgentConditionInput
  ) {
    updateAgent(input: $input, condition: $condition) {
      id
      type
      name
      nickname
      email_sha1
      email
      homepage
      username
      created
      modified
    }
  }
`;
export const deleteAgent = /* GraphQL */ `
  mutation DeleteAgent(
    $input: DeleteAgentInput!
    $condition: ModelAgentConditionInput
  ) {
    deleteAgent(input: $input, condition: $condition) {
      id
      type
      name
      nickname
      email_sha1
      email
      homepage
      username
      created
      modified
    }
  }
`;