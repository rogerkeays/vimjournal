"
" vimjournal plugin
"
" to install, save (or symlink) to your $HOME/.vim/ftdetect directory
"
" tagging scheme:
"   / category          (file)
"   + person, duration  (global)
"   # topic             (global)
"   = project           (global)
"   ! problem, goal     (global)
"   > context           (entry)
"   @ place             (global)
"   : data, url         (entry)
"   & skips             (entry)
"

autocmd BufRead,BufNewFile *.log setl filetype=vimjournal

" presentation and code folding
autocmd FileType vimjournal setl autoindent sw=2 ts=8 nrformats=
autocmd FileType vimjournal setl nowrap linebreak breakindent showbreak=>\ 
autocmd FileType vimjournal setl foldmethod=manual foldtext=getline(v:foldstart-1) fillchars=fold:\ 
autocmd FileType vimjournal setl foldexpr=getline(v\:lnum)->strgetchar(15)==124?'0'\:1

" slower, but more correct
"autocmd FileType vimjournal setl foldexpr=strcharpart(getline(v\:lnum+1),14,2)=~'[-_>.x=~+*]\|'?'<1'\:1

" keyboard shortcuts
autocmd FileType vimjournal nnoremap <TAB> za
autocmd FileType vimjournal nnoremap <S-TAB> :set invwrap<CR>
autocmd FileType vimjournal nnoremap <C-l> :Explore<CR>
autocmd FileType vimjournal nnoremap <C-o> yyp:s/.\|.*/>\| <CR>A
autocmd FileType vimjournal nnoremap <C-p> yyP:s/.\|.*/>\| <CR>A
autocmd FileType vimjournal nnoremap <C-t> :call AppendRecord()<CR>A
autocmd FileType vimjournal inoremap <C-t> <C-R>=strftime("%Y%m%d_%H%M")<CR>
autocmd FileType vimjournal inoremap <C-x> ✘
autocmd FileType vimjournal inoremap <C-z> ✔
autocmd FileType vimjournal inoremap <TAB> <C-P>
autocmd FileType vimjournal inoremap <S-TAB> <C-N>

" autocomplete configuration
autocmd FileType vimjournal setl complete=.                      " search only current buffer (faster)
autocmd FileType vimjournal setl completeopt=                    " disable autocomplete menu (annoying)
autocmd FileType vimjournal setl iskeyword+=@-@,/,#,=,!,>,:,-    " include tag symbols in autocomplete
autocmd FileType vimjournal setl iskeyword-=_                    " word navigation inside timestamps

" jump to the end of the file when first loaded
function JumpEnd()
  if !exists("b:vimjournal_jumped")
    normal Gzm
    let b:vimjournal_jumped = 1
  endif
  setl foldmethod=expr
endfunction
autocmd FileType vimjournal call JumpEnd()

" open a new record without unfolding existing records
function AppendRecord()
  call setreg("t", strftime("%Y%m%d_%H%M >| \n"))
  normal G"tp
endfunction

" opens the quickfix list in a tab with no formatting
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

  " hide the quickfix metadata
  syn match metadata /^.*|[0-9]\+ col [-0-9]\+| / transparent conceal
endfunction
autocmd FileType vimjournal hi QuickFixLine ctermbg=None

" filter the current file using a regexp and display the results in a separate tab
" if no regexp is supplied, the last search pattern is used
function GrepJournals(regexp, files)
  execute 'vimgrep /'.a:regexp.'/j '.a:files
  call DisplayVimjournalQuickfixTab()
endfunction
autocmd FileType vimjournal command! -nargs=? Filter call GrepJournals(<f-args>, '%')
autocmd FileType vimjournal command! -nargs=? Find call GrepJournals(<f-args>, '*.log')

"
" syntax definitions: uses *.log because they don't work with `FileType vimjournal`
" unless you create a separate file in .vim/syntax, which makes installation more screwy
"

" space then a tag character followed by a non-space, another tag or the end of line
autocmd BufRead *.log syn match Tags " [/+#=!>@:&]\([^ |]\| \+[/+#=!>@:&]\| *$\).*" contained

autocmd BufRead *.log syn keyword Bar │ contained
autocmd BufRead *.log syn match Date "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}[<! ].|" contained
autocmd BufRead *.log syn match OneStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}[<! ][>_x.1 ]|.*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match TwoStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}[<! ][-v2]|.*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match ThreeStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}[<! ][=~3]|.*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FourStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}[<! ][+^4]|.*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FiveStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}[<! ][*5]|.*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match Heading "^==[^ ].*$"
autocmd BufRead *.log syn match Heading "^## .*$"
autocmd BufRead *.log syn match Comments "^//.*$"
autocmd BufRead *.log syn match Comments " // .*$"

autocmd FileType vimjournal hi Bar ctermfg=darkgrey
autocmd FileType vimjournal hi Date ctermfg=darkgrey
autocmd FileType vimjournal hi Tags ctermfg=darkgrey
autocmd FileType vimjournal hi OneStar ctermfg=darkgrey
autocmd FileType vimjournal hi TwoStar ctermfg=lightgrey
autocmd FileType vimjournal hi ThreeStar ctermfg=darkcyan
autocmd FileType vimjournal hi FourStar ctermfg=cyan
autocmd FileType vimjournal hi FiveStar ctermfg=white
autocmd FileType vimjournal hi Heading ctermfg=white
autocmd FileType vimjournal hi Comments ctermfg=lightgreen
autocmd FileType vimjournal hi Reference ctermfg=lightyellow
autocmd FileType vimjournal hi Folded ctermbg=NONE ctermfg=darkgrey

"
" functions to find anacronisms
"
" forward search is mapped to <C-n>
" reverse search is mapped to <C-h>
"
function FindNextAnac()
  while line(".") != line("$")
    if getline(".")[0:12] > getline(line(".") + 1)[0:12]
      break
    endif
    normal j
  endwhile
endfunction
autocmd FileType vimjournal nnoremap <C-n> :call FindNextAnac()<CR>

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

" keep the screen still when toggling wrap
" credit: Vivian De Smedt
" https://vi.stackexchange.com/questions/43083/can-you-prevent-then-screen-jumping-when-toggling-the-wrap-option
function GetWinWidth()
  let ret = winwidth(0)

  if &signcolumn !=# 'no'
    let ret = ret - 2
  endif

  if &number
    let ret = ret - max([&numberwidth, float2nr(log10(line('$'))) + 2])
  endif

  return ret
endfunction

function WrapRowDelta(line1, line2)
  " This is an approximation that could be improved :-)
  let ret = 0
  let max_width = GetWinWidth()
  for i in range(a:line1, a:line2 - 1)
    if foldclosed(i) < 0
      let ret = ret + float2nr(ceil(str2float(len(getline(i)) + 1) / max_width))
    elseif foldclosed(i) == i
      let ret = ret + 1
    endif
  endfor
  return ret
endfunction

function! NoWrapRowDelta(line1, line2)
  " return a:line2 - a:line1
  let ret = 0
  for i in range(a:line1, a:line2 - 1)
    if foldclosed(i) < 0
      let ret = ret + 1
    elseif foldclosed(i) == i
      let ret = ret + 1
    endif
  endfor
  return ret
endfunction

function! CorrectCursorScroll()
  if &wrap
    " Estimation of the previous space:
    let nonwrap_row_delta = NoWrapRowDelta(line('w0'), line('.'))

    " Value of the current space
    let wrap_row_delta = screenpos(0, line('.'), col('.')).row - 1
    while wrap_row_delta > nonwrap_row_delta
      exe "normal! \<C-e>"
      let r = screenpos(0, line('.'), col('.')).row - 1
      if wrap_row_delta == r
        break
      endif
      let wrap_row_delta = r
    endwhile
    if wrap_row_delta < nonwrap_row_delta
      exe "normal! \<C-y>"
    endif
  else
    " Estimation of the previous space:
    let wrap_row_delta = WrapRowDelta(line('w0'), line('.'))

    " Value of the current space
    let nonwrap_row_delta = NoWrapRowDelta(line('w0'), line('.'))
    while wrap_row_delta > nonwrap_row_delta
      exe "normal! \<C-y>"
      let r = NoWrapRowDelta(line('w0'), line('.'))
      if nonwrap_row_delta == r
        break
      endif
      let nonwrap_row_delta = r
    endwhile
    if wrap_row_delta < nonwrap_row_delta
      exe "normal! \<C-e>"
    endif
  endif
endfunction

autocmd! OptionSet wrap call CorrectCursorScroll()

