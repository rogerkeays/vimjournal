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
autocmd FileType vimjournal setl foldmethod=expr foldtext=getline(v:foldstart) fillchars=
autocmd FileType vimjournal setl foldexpr=getline(v\:lnum+1)->strgetchar(14)==124?'<1'\:1

" slower, but more correct
"autocmd FileType vimjournal setl foldexpr=strcharpart(getline(v\:lnum+1),14,2)=~'\|[-_>x=~+*]'?'<1'\:1

" keyboard shortcuts
autocmd FileType vimjournal nnoremap <TAB> za
autocmd FileType vimjournal nnoremap <S-TAB> :set invwrap<CR>
autocmd FileType vimjournal nnoremap <C-l> :Explore<CR>
autocmd FileType vimjournal nnoremap <C-o> yyp:s/.\|.*/ \|> <CR>A
autocmd FileType vimjournal nnoremap <C-t> :call AppendRecord()<CR>A
autocmd FileType vimjournal inoremap <C-t> // <C-R>=strftime("%Y%m%d_%H%M")<CR> 
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
    let b:vimjournal_jumped = 1
    normal Gzm
  endif
endfunction
autocmd FileType vimjournal call JumpEnd()

" open a new record without unfolding existing records
function AppendRecord()
  call setreg("t", strftime("%Y%m%d_%H%M |> \n"))
  normal G"tp
endfunction

" toggle wrap, keeping the screen anchored on the cursor line
" note: broken because <C-e> and <C-y> don't support smooth scrolling
function ToggleWrap()
  let original_row = winline()
  setl invwrap
  let diff = winline() - original_row
  if diff > 0
    execute "normal ".diff."\<C-e>"
  else
    execute "normal ".diff."\<C-y>"
  endif
endfunction

"
" syntax definitions: uses *.log because they don't work with `FileType vimjournal`
" unless you create a separate file in .vim/syntax, which makes installation more screwy
"

" space then a tag character followed by a non-space, another tag or the end of line
autocmd BufRead *.log syn match Tags " [/+#=!>@:&]\([^ |]\| \+[/+#=!>@:&]\| *$\).*" contained

autocmd BufRead *.log syn keyword Bar │ contained
autocmd BufRead *.log syn match Date "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\}" contained
autocmd BufRead *.log syn match NoStars "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[>_].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match OneStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[x].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match TwoStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[-].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match ThreeStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[=~].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FourStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[+].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FiveStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[*].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match Heading "^==[^ ].*$"
autocmd BufRead *.log syn match Heading "^## .*$"
autocmd BufRead *.log syn match Comment "^//.*$"
autocmd BufRead *.log syn match Comment " // .*$"

autocmd FileType vimjournal hi Bar ctermfg=darkgrey
autocmd FileType vimjournal hi Date ctermfg=darkgrey
autocmd FileType vimjournal hi Tags ctermfg=darkgrey
autocmd FileType vimjournal hi NoStars ctermfg=lightgrey
autocmd FileType vimjournal hi OneStar ctermfg=brown
autocmd FileType vimjournal hi TwoStar ctermfg=red
autocmd FileType vimjournal hi ThreeStar ctermfg=lightblue
autocmd FileType vimjournal hi FourStar ctermfg=green
autocmd FileType vimjournal hi FiveStar ctermfg=yellow
autocmd FileType vimjournal hi Heading ctermfg=white
autocmd FileType vimjournal hi Comment ctermfg=lightgreen
autocmd FileType vimjournal hi Reference ctermfg=lightyellow
autocmd FileType vimjournal hi Folded ctermbg=NONE ctermfg=NONE

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
    if getline(".")[0:12] < getline(line(".") - 1)[0:12]
      break
    endif
    normal k
  endwhile
endfunction
autocmd FileType vimjournal nnoremap <C-h> :call FindLastAnac()<CR>

