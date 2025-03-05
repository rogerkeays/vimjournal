"
" vimjournal plugin
"
" to install, save (or symlink) to your $HOME/.vim/ftdetect directory
"
" tagging scheme:
"   / category
"   + person, duration
"   # topic
"   = project
"   ! problem
"   > context
"   @ place
"   : data
"   & skips
"
autocmd BufRead,BufNewFile *.log setl filetype=vimjournal

" presentation and code folding
autocmd FileType vimjournal setl autoindent sw=2 ts=8 nrformats=
autocmd FileType vimjournal setl nowrap linebreak breakindent showbreak=\\|\ 
autocmd FileType vimjournal setl foldmethod=manual foldtext=getline(v:foldstart-1) fillchars=fold:\ 
autocmd FileType vimjournal setl foldexpr=getline(v\:lnum)[15]=='\|'?'0'\:1 " imprecise, but fast
"autocmd FileType vimjournal setl foldexpr=strcharpart(getline(v\:lnum+1),14,2)=~'[-x=+*>]\|'?'<1'\:1

" keyboard shortcuts
autocmd FileType vimjournal nnoremap <TAB> za
autocmd FileType vimjournal nnoremap <S-TAB> :call ToggleWrap()<CR>
autocmd FileType vimjournal nnoremap <C-l> :Explore<CR>
autocmd FileType vimjournal nnoremap <C-o> yyp:s/.\|.*/>\| <CR>A
autocmd FileType vimjournal nnoremap <C-p> yyP:s/.\|.*/>\| <CR>A
autocmd FileType vimjournal nnoremap <C-t> :call AppendRecord()<CR>A
autocmd FileType vimjournal inoremap <C-t> <C-R>=strftime("%Y%m%d_%H%M")<CR>
autocmd FileType vimjournal inoremap <TAB> <C-P>
autocmd FileType vimjournal inoremap <S-TAB> <C-N>

" autocomplete configuration
autocmd FileType vimjournal setl complete=.                      " search only current buffer (faster)
autocmd FileType vimjournal setl completeopt=                    " disable autocomplete menu (annoying)
autocmd FileType vimjournal setl iskeyword+=@-@,/,#,=,!,>,:,-    " include tag symbols in autocomplete
autocmd FileType vimjournal setl iskeyword-=_                    " word navigation inside timestamps

" syntax definition use *.log because using `FileType vimjournal` requires a separate file in .vim/syntax
autocmd BufRead *.log syn keyword Bar │ contained
autocmd BufRead *.log syn match Date `^[0-9X]\{8\}_[0-9X]\{4\} .|` contained
autocmd BufRead *.log syn match NoStars `^[0-9X]\{8\}_[0-9X]\{4\} [> ]|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match OneStar `^[0-9X]\{8\}_[0-9X]\{4\} [x1]|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match TwoStar `^[0-9X]\{8\}_[0-9X]\{4\} [-2]|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match ThreeStar `^[0-9X]\{8\}_[0-9X]\{4\} [=3]|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match FourStar `^[0-9X]\{8\}_[0-9X]\{4\} [+4]|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match FiveStar `^[0-9X]\{8\}_[0-9X]\{4\} [*5]|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match Check `^[0-9X]\{8\}_[0-9X]\{4\} !|.*$` contains=Bar,Date,Tags
autocmd BufRead *.log syn match Heading `^==[^ ].*$`
autocmd BufRead *.log syn match Heading `^## .*$`
autocmd BufRead *.log syn match Comments `^//.*$`
autocmd BufRead *.log syn match Comments ` // .*$`

" space then a tag character followed by a non-space, another tag or the end of line
autocmd BufRead *.log syn match Tags ` [/+#=!>@:&]\([^ |]\| \+[/+#=!>@:&]\| *$\).*` contained

autocmd FileType vimjournal hi Bar ctermfg=darkgrey
autocmd FileType vimjournal hi Date ctermfg=darkgrey
autocmd FileType vimjournal hi Tags ctermfg=darkgrey
autocmd FileType vimjournal hi NoStars ctermfg=darkgrey
autocmd FileType vimjournal hi OneStar ctermfg=darkgrey
autocmd FileType vimjournal hi TwoStar ctermfg=lightgrey
autocmd FileType vimjournal hi ThreeStar ctermfg=darkcyan
autocmd FileType vimjournal hi FourStar ctermfg=cyan
autocmd FileType vimjournal hi FiveStar ctermfg=white
autocmd FileType vimjournal hi Check ctermfg=yellow
autocmd FileType vimjournal hi Heading ctermfg=white
autocmd FileType vimjournal hi Comments ctermfg=lightgreen
autocmd FileType vimjournal hi Reference ctermfg=lightyellow
autocmd FileType vimjournal hi Folded ctermbg=NONE ctermfg=darkgrey
autocmd FileType vimjournal hi CursorLine ctermbg=234 ctermfg=NONE

" jump to the end of the journal when first loaded
function JumpEnd()
  if !exists("b:vimjournal_jumped")
    normal Gzm
    let b:vimjournal_jumped = 1
  endif
  setl foldmethod=expr
endfunction
autocmd Filetype vimjournal call JumpEnd()

" open a new record at the end of the journal without unfolding existing records
function AppendRecord()
  call setreg("t", strftime("%Y%m%d_%H%M >| \n"))
  normal G"tp
endfunction

" keep the screen fixed on the selected line when changing the wrap mode
function ToggleWrap()
  let original_row = winline()
  setl invwrap
  let offset = winline() - original_row
  if offset > 0
    execute "normal ".offset."\<C-e>"
  elseif offset < 0
    execute "normal ".(-1 * offset)."\<C-y>"
  endif
endfunction

" find anacronisms: <C-h> for reverse search
function FindLastAnac()
  while line(".") != 1
    let current = getline(".")
    if trim(current) != "" && current[0:12] < getline(line(".") - 1)[0:12]
      break
    endif
    normal k
  endwhile
endfunction
autocmd FileType vimjournal nnoremap <C-h> :call FindLastAnac()<CR>

" find anacronisms: <C-n> for forward search
function FindNextAnac()
  while line(".") != line("$")
    if getline(".")[0:12] > getline(line(".") + 1)[0:12]
      break
    endif
    normal j
  endwhile
endfunction
autocmd FileType vimjournal nnoremap <C-n> :call FindNextAnac()<CR>

" filter the current file using a regexp and display the results in a separate tab
" if no regexp is supplied, the last search pattern is used
function GrepHeaders(regexp, files)
  execute 'vimgrep `^[0-9X]\{8\}_[0-9X]\{4\} .|.*'.a:regexp.'`j '.a:files
  call DisplayVimjournalQuickfixTab()
endfunction
function GrepIndentedHeaders(regexp, files)
  execute 'vimgrep `^[0-9X]\{8\}_[0-9X]\{4\} .|  .*'.a:regexp.'`j '.a:files
  call DisplayVimjournalQuickfixTab()
endfunction
function GrepOutdentedHeaders(regexp, files)
  execute 'vimgrep `^[0-9X]\{8\}_[0-9X]\{4\} .| [^ ].*'.a:regexp.'`j '.a:files
  call DisplayVimjournalQuickfixTab()
endfunction
function GrepContent(regexp, files)
  execute 'vimgrep `'.a:regexp.'`j '.a:files
  call DisplayVimjournalQuickfixTab()
endfunction
autocmd FileType vimjournal command! -nargs=? Find call GrepHeaders(<f-args>, '%:p:h/*.log')
autocmd FileType vimjournal command! -nargs=? FindIndented call GrepIndentedHeaders(<f-args>, '%:p:h/*.log')
autocmd FileType vimjournal command! -nargs=? FindOutdented call GrepOutdentedHeaders(<f-args>, '%:p:h/*.log')
autocmd FileType vimjournal command! -nargs=? FindContent call GrepContent(<f-args>, '%:p:h/*.log')
autocmd FileType vimjournal command! -nargs=? LFind call GrepHeaders(<f-args>, '%')
autocmd FileType vimjournal command! -nargs=? LFindIndented call GrepIndentedHeaders(<f-args>, '%')
autocmd FileType vimjournal command! -nargs=? LFindOutdented call GrepOutdentedHeaders(<f-args>, '%')
autocmd FileType vimjournal command! -nargs=? LFindContent call GrepContent(<f-args>, '%')

" display the quickfix list in a tab with no formatting
function DisplayVimjournalQuickfixTab()
  if !exists("g:vimjournal_copened")
    $tab copen
    set switchbuf+=usetab nowrap conceallevel=2 concealcursor=nc
    let g:vimjournal_copened = 1

    " switchbuf=newtab is ignored when there are no splits, so we use :tab explicitely
    " https://vi.stackexchange.com/questions/6996
    nnoremap <buffer> <Enter> :-tab .cc<CR>
  else
    $tabnext
    normal 1G
  endif
  syn match metadata /^.*|[0-9]\+ col [-0-9]\+| / transparent conceal
endfunction
autocmd FileType vimjournal hi QuickFixLine ctermbg=None

" sort the quickfix list by stars and update the view
function SortByStars()
  call setqflist(sort(getqflist(), { x, y -> y.text[14]->ToStars() - x.text[14]->ToStars() }))
  call DisplayVimjournalQuickfixTab()
endfunction
autocmd FileType vimjournal command! StarSort call SortByStars()

" convert a text character to the number of stars represented by that character
function ToStars(char)
  if a:char == '*' || a:char == '5' | return 5
  elseif a:char == '+' || a:char == '4' | return 4
  elseif a:char == '=' || a:char == '3' | return 3
  elseif a:char == '-' || a:char == '2' | return 2
  elseif a:char == 'x' || a:char == '1' | return 1
  else | return 0 | endif
endfunction

" sort the quickfix list by title and update the view
function SortByTitle()
  call setqflist(sort(getqflist(), { x, y -> y.text[16:] < x.text[16:] }))
  call DisplayVimjournalQuickfixTab()
endfunction
autocmd FileType vimjournal command! TitleSort call SortByTitle()

" sort the quickfix list by reference tags and update the view
function SortByRef()
  call setqflist(sort(getqflist(), { x, y -> y.text->GetRef() < x.text->GetRef() }))
  call DisplayVimjournalQuickfixTab()
endfunction
autocmd FileType vimjournal command! RefSort call SortByRef()

" extract the first reference tag starting with a :
function GetRef(text)
  return a:text->matchstr(" :[^ ]\\+")
endfunction

" execute commands on header lines
autocmd FileType vimjournal command! -nargs=1 Headers g`^[0-9X]\{8}_[0-9X]\{4} .|`<args>

