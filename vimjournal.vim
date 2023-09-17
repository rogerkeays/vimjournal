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
autocmd FileType vimjournal setl wrap linebreak breakindent showbreak=>\ 
autocmd FileType vimjournal setl foldmethod=expr foldtext=getline(v:foldstart) fillchars= 
autocmd FileType vimjournal setl foldexpr=strcharpart(getline(v\:lnum),14,2)=~'\|[-_>x=~+*]'?'>1'\:1

" keyboard shortcuts
autocmd FileType vimjournal nnoremap <TAB> za
autocmd FileType vimjournal nnoremap <C-l> :Explore<CR>
autocmd FileType vimjournal nnoremap <C-o> yyp:s/.\|.*/ \|> <CR>A
autocmd FileType vimjournal nnoremap <C-t> Go<C-R>=strftime("%Y%m%d_%H%M")<CR> \|> 
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

"
" syntax definitions: uses *.log because they don't work with `FileType vimjournal`
" unless you create a separate file in .vim/syntax, which makes installation more screwy
"

" space then a tag character followed by a non-space, another tag or the end of line
autocmd BufRead *.log syn match Tags " [/+#=!>@:&]\([^ |]\| \+[/+#=!>@:&]\| *$\).*" contained

autocmd BufRead *.log syn keyword Bar │ contained
autocmd BufRead *.log syn match Date "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |" contained
autocmd BufRead *.log syn match NoStars "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[>_] .*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match OneStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[x].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match TwoStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[-].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match ThreeStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[=~].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FourStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[+].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match FiveStar "^[0-9A-Za-z]\{8\}_[0-9A-Za-z]\{4\} |[*].*$" contains=Bar,Date,Tags
autocmd BufRead *.log syn match Heading "^==[^ ].*$"
autocmd BufRead *.log syn match Heading "^## .*$"
autocmd BufRead *.log syn match Comment "^//.*$"
autocmd BufRead *.log syn match Comment " // .*$"

autocmd BufRead *.log hi Bar ctermfg=lightgrey
autocmd BufRead *.log hi Date ctermfg=lightgrey
autocmd BufRead *.log hi Tags ctermfg=235
autocmd BufRead *.log hi NoStars ctermfg=lightgrey
autocmd BufRead *.log hi OneStar ctermfg=brown
autocmd BufRead *.log hi TwoStar ctermfg=red
autocmd BufRead *.log hi ThreeStar ctermfg=lightblue
autocmd BufRead *.log hi FourStar ctermfg=green
autocmd BufRead *.log hi FiveStar ctermfg=yellow
autocmd BufRead *.log hi Heading ctermfg=white
autocmd BufRead *.log hi Comment ctermfg=lightgreen
autocmd BufRead *.log hi Reference ctermfg=lightyellow

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

