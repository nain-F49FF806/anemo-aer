# Anemo - Private local storage

[![Bazel Android](https://github.com/2bllw8/anemo/actions/workflows/bazel.yml/badge.svg)](https://github.com/2bllw8/anemo/actions/workflows/bazel.yml)

Anemo is a private local storage application that instead of providing a stand-alone UI it hooks
into various components of the Android operative system making it feel as native as possible.
Moreover it provides ways for the user to save content from other apps as files.

## Features

- [x] File storage available in the default Files application (the DocumentsProviderUI)
- [x] Lock access to the storage using a quick tile
- [x] Saving text snippets from selected text as "Snippet" files
- [x] Import from the share Android functionality
    - Audio
    - Images
    - PDF files
    - Videos

### To do

Some of the planned features

- [ ] Make a nice UI for importing snippets
- [ ] Password for unlocking access to the files
